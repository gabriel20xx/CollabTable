package com.collabtable.app.data.api

import android.content.Context
import com.collabtable.app.data.preferences.PreferencesManager
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
                        .header("Authorization", "Bearer $password")
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

    fun initialize(context: Context) {
        this.context = context.applicationContext
        val prefs = PreferencesManager.getInstance(context)
        baseUrl = prefs.getServerUrl()
        retrofit = buildRetrofit()
    }

    fun setBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
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
