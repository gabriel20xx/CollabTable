package com.collabtable.app.data.api

import android.content.Context
import com.collabtable.app.data.preferences.PreferencesManager
import android.os.Build
import android.net.Uri
import com.collabtable.app.utils.Logger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl: String = "http://10.0.2.2:3000/api/" // Default for Android emulator
    private var retrofit: Retrofit? = null
    private var context: Context? = null

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    private val authInterceptor =
        Interceptor { chain ->
            val request = chain.request()
            val password =
                context?.let {
                    PreferencesManager.getInstance(it).getServerPassword()
                }

            val newRequest =
                if (!password.isNullOrBlank()) {
                    request.newBuilder()
                        .header("Authorization", "Bearer ${'$'}password")
                        .build()
                } else {
                    request
                }

            chain.proceed(newRequest)
        }

    private val okHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") || fingerprint.contains("emulator") ||
            model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for") ||
            brand.startsWith("generic") || device.startsWith("generic") || product.contains("sdk")
    }

    private fun ensureTrailingSlash(url: String): String = if (url.endsWith("/")) url else "$url/"

    private fun normalizeForAndroidEmulator(rawUrl: String): String {
        // Remap common hostnames that are unreachable from the Android emulator to host loopback
        // - localhost / 127.0.0.1 / host.docker.internal -> 10.0.2.2 (emulator-only)
        // Leave other hosts unchanged.
        if (!isEmulator()) return ensureTrailingSlash(rawUrl)
        return try {
            val uri = Uri.parse(rawUrl)
            val host = uri.host?.lowercase()
            val needsRemap = host == "localhost" || host == "127.0.0.1" || host == "host.docker.internal"
            if (needsRemap) {
                val scheme = uri.scheme ?: "http"
                val portPart = if (uri.port != -1) ":${uri.port}" else ""
                val pathPart = uri.encodedPath ?: "/"
                val queryPart = if (uri.encodedQuery != null) "?${uri.encodedQuery}" else ""
                val remapped = "$scheme://10.0.2.2$portPart$pathPart$queryPart"
                ensureTrailingSlash(if (remapped.contains("/api")) remapped else remapped.trimEnd('/') + "/api/")
            } else {
                // Ensure /api/ suffix present
                val ensuredApi = if (rawUrl.contains("/api")) rawUrl else rawUrl.trimEnd('/') + "/api/"
                ensureTrailingSlash(ensuredApi)
            }
        } catch (e: Exception) {
            ensureTrailingSlash(rawUrl)
        }
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val prefs = PreferencesManager.getInstance(context)
        baseUrl = normalizeForAndroidEmulator(prefs.getServerUrl())
        try {
            Logger.i("HTTP", "API base URL initialized to $baseUrl")
        } catch (_: Exception) { }
        retrofit = buildRetrofit()
    }

    fun setBaseUrl(url: String) {
        baseUrl = normalizeForAndroidEmulator(url)
        try {
            Logger.i("HTTP", "API base URL set to $baseUrl")
        } catch (_: Exception) { }
        retrofit = buildRetrofit()
    }

    val api: CollabTableApi
        get() {
            if (retrofit == null) {
                retrofit = buildRetrofit()
            }
            return retrofit!!.create(CollabTableApi::class.java)
        }
}
