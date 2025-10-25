package com.collabtable.app.data.api

import android.content.Context
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.utils.Logger
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.util.UUID
import java.util.concurrent.TimeUnit

private data class MessageEnvelope(
    val id: String? = null,
    val type: String,
    val payload: Any? = null,
)

object WebSocketSyncClient {
    private val gson = Gson()

    private val logging =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

    private fun buildWebSocketUrl(httpApiBaseUrl: String): String {
        // Expecting something like: http://host:port/api/
        var url = httpApiBaseUrl
        // Ensure trailing slash removed for manipulation
        if (url.endsWith("/")) url = url.dropLast(1)

        // Replace scheme
        url =
            when {
                url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
                url.startsWith("http://") -> "ws://" + url.removePrefix("http://")
                else -> "ws://$url" // best effort
            }

        // Replace trailing /api with /api/ws
        return if (url.endsWith("/api")) "$url/ws" else "$url/api/ws"
    }

    suspend fun sync(
        context: Context,
        request: SyncRequest,
    ): Result<SyncResponse> =
        withContext(Dispatchers.IO) {
            val prefs = PreferencesManager.getInstance(context)
            val baseUrl = prefs.getServerUrl()
            val password = prefs.getServerPassword()
            val wsUrl = buildWebSocketUrl(baseUrl)
            val messageId = UUID.randomUUID().toString()

            val deferred = CompletableDeferred<Result<SyncResponse>>()
            var socket: WebSocket? = null

            val httpRequestBuilder = Request.Builder().url(wsUrl)
            if (!password.isNullOrBlank()) {
                httpRequestBuilder.addHeader("Authorization", "Bearer $password")
            }
            val httpRequest = httpRequestBuilder.build()

            val listener =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        // Send sync message when opened
                        val envelope =
                            MessageEnvelope(
                                id = messageId,
                                type = "sync",
                                payload = request,
                            )
                        val json = gson.toJson(envelope)
                        webSocket.send(json)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        try {
                            val root = gson.fromJson(text, JsonObject::class.java)
                            val type = root.get("type")?.asString
                            val id = root.get("id")?.asString
                            if (type == "syncResponse" && id == messageId) {
                                val payload = root.getAsJsonObject("payload")
                                val resp = gson.fromJson(payload, SyncResponse::class.java)
                                if (!deferred.isCompleted) {
                                    deferred.complete(Result.success(resp))
                                    webSocket.close(1000, "done")
                                }
                            } else if (type == "error" && !deferred.isCompleted) {
                                val msg = root.getAsJsonObject("payload")?.get("message")?.asString ?: "WebSocket error"
                                deferred.complete(Result.failure(Exception(msg)))
                                webSocket.close(1002, "error")
                            }
                        } catch (e: Exception) {
                            if (!deferred.isCompleted) {
                                deferred.complete(Result.failure(e))
                            }
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        // 1008 indicates policy violation, used here for Unauthorized
                        if (!deferred.isCompleted) {
                            if (code == 1008) {
                                deferred.complete(Result.failure(Exception("Unauthorized (WS 1008)")))
                            } else {
                                deferred.complete(Result.failure(Exception("WebSocket closing ($code): $reason")))
                            }
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        if (!deferred.isCompleted) {
                            deferred.complete(Result.failure(t))
                        }
                    }
                }

            try {
                socket = client.newWebSocket(httpRequest, listener)
                // Wait up to 30s for response (WS roundtrip)
                val result = withTimeout(30_000) { deferred.await() }
                return@withContext result
            } catch (e: Exception) {
                Logger.w("WS", "Falling back to HTTP sync: ${e.message}")
                return@withContext Result.failure(e)
            } finally {
                // OkHttp cleans up sockets automatically when no references remain
                // but we try to close if still open
                socket?.close(1000, null)
            }
        }
}
