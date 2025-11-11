package com.collabtable.app.ui.screens

import android.net.Uri
import android.os.Build
import android.os.NetworkOnMainThreadException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class ServerSetupViewModel(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {
    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _validationResult = MutableStateFlow<Boolean?>(null)
    val validationResult: StateFlow<Boolean?> = _validationResult.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val okHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    fun validateAndSaveServerUrl(
        url: String,
        password: String,
    ) {
        viewModelScope.launch {
            _isValidating.value = true
            _validationError.value = null
            _validationResult.value = null

            try {
                // Normalize URL - add protocol if missing
                var normalizedUrl = url.trim()

                // Check if URL has a protocol
                val hasProtocol = normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")
                val hasPort = normalizedUrl.contains(":")

                // Add http:// if no protocol specified
                if (!hasProtocol) {
                    normalizedUrl = "http://$normalizedUrl"
                }

                // Add default port if no port specified
                if (!hasPort || normalizedUrl.matches(Regex("^https?://.+$")) && !normalizedUrl.substringAfter("://").contains(":")) {
                    // Extract protocol and host
                    val protocol = if (normalizedUrl.startsWith("https://")) "https" else "http"
                    val hostAndPath = normalizedUrl.substringAfter("://")
                    val host = if (hostAndPath.contains("/")) hostAndPath.substringBefore("/") else hostAndPath
                    val path = if (hostAndPath.contains("/")) "/" + hostAndPath.substringAfter("/") else ""

                    // Add default port based on protocol
                    val defaultPort = if (protocol == "https") "443" else "80"
                    normalizedUrl = "$protocol://$host:$defaultPort$path"
                }

                // Remove trailing slash if present for consistent handling
                normalizedUrl = normalizedUrl.trimEnd('/')

                // Add /api/ if not present
                if (!normalizedUrl.contains("/api")) {
                    normalizedUrl = "$normalizedUrl/api"
                }

                // Ensure it ends with /
                normalizedUrl = if (normalizedUrl.endsWith("/")) normalizedUrl else "$normalizedUrl/"

                // Validate password is not empty (trim leading/trailing spaces) and not a placeholder
                val trimmedPassword = password.trim()
                if (trimmedPassword.isBlank()) {
                    _validationError.value = "Password cannot be empty"
                    _isValidating.value = false
                    return@launch
                }
                if (trimmedPassword == "\$password") {
                    _validationError.value = "Enter the actual server password, not the placeholder $password"
                    _isValidating.value = false
                    return@launch
                }

                // On physical devices, block local-only hosts that won't resolve
                val isEmulator =
                    run {
                        val fp = Build.FINGERPRINT.lowercase()
                        val model = Build.MODEL.lowercase()
                        val brand = Build.BRAND.lowercase()
                        val device = Build.DEVICE.lowercase()
                        val product = Build.PRODUCT.lowercase()
                        fp.contains("generic") ||
                            fp.contains("emulator") ||
                            model.contains("google_sdk") ||
                            model.contains("emulator") ||
                            model.contains("android sdk built for") ||
                            brand.startsWith("generic") ||
                            device.startsWith("generic") ||
                            product.contains("sdk")
                    }
                val host =
                    try {
                        Uri.parse(normalizedUrl).host?.lowercase()
                    } catch (_: Exception) {
                        null
                    }
                val localOnlyHosts = setOf("localhost", "127.0.0.1", "host.docker.internal", "10.0.2.2")
                if (!isEmulator && host != null && host in localOnlyHosts) {
                    _validationError.value = "On a physical device, use your computer's LAN IP (e.g., 192.168.x.x:3000) instead of '$host'."
                    _isValidating.value = false
                    return@launch
                }

                // Try to reach the health endpoint (no auth required)
                val healthUrl = normalizedUrl.replace("/api/", "/health")
                val healthRequest =
                    Request
                        .Builder()
                        .url(healthUrl)
                        .get()
                        .build()

                try {
                    // Execute network call on IO dispatcher
                    val healthResponse =
                        withContext(Dispatchers.IO) {
                            okHttpClient.newCall(healthRequest).execute()
                        }

                    if (!healthResponse.isSuccessful) {
                        val errorMessage =
                            when (healthResponse.code) {
                                404 -> "Health endpoint not found. Check server URL."
                                500 -> "Server internal error. Check server logs."
                                503 -> "Service unavailable. Server may be starting up."
                                else -> "Server returned HTTP ${healthResponse.code}"
                            }
                        healthResponse.close()
                        _validationError.value = errorMessage
                        _isValidating.value = false
                        return@launch
                    }
                    healthResponse.close()

                    // Try to upgrade to HTTPS if currently using HTTP
                    var finalUrl = normalizedUrl
                    if (normalizedUrl.startsWith("http://")) {
                        // Replace http:// with https:// and change port 80 to 443
                        var httpsUrl = normalizedUrl.replace("http://", "https://")
                        if (httpsUrl.contains(":80/")) {
                            httpsUrl = httpsUrl.replace(":80/", ":443/")
                        }

                        val httpsHealthUrl = httpsUrl.replace("/api/", "/health")
                        val httpsHealthRequest =
                            Request
                                .Builder()
                                .url(httpsHealthUrl)
                                .get()
                                .build()

                        try {
                            // Execute HTTPS check on IO dispatcher
                            val httpsResponse =
                                withContext(Dispatchers.IO) {
                                    okHttpClient.newCall(httpsHealthRequest).execute()
                                }
                            if (httpsResponse.isSuccessful) {
                                // HTTPS works! Upgrade to it
                                finalUrl = httpsUrl
                            }
                            httpsResponse.close()
                        } catch (e: Exception) {
                            // HTTPS doesn't work, stick with HTTP
                        }
                    }

                    // Now validate the password by making an authenticated request
                    val testUrl = "${finalUrl}lists"
                    val authRequest =
                        Request
                            .Builder()
                            .url(testUrl)
                            .header("Authorization", "Bearer $trimmedPassword")
                            .get()
                            .build()

                    // Execute auth check on IO dispatcher
                    val authResponse =
                        withContext(Dispatchers.IO) {
                            okHttpClient.newCall(authRequest).execute()
                        }

                    if (authResponse.isSuccessful) {
                        // Password is valid, save both URL and password
                        preferencesManager.setServerUrl(finalUrl)
                        preferencesManager.setServerPassword(trimmedPassword)
                        // Reset sync baseline for a fresh initial sync on new server
                        preferencesManager.clearSyncState()
                        preferencesManager.setIsFirstRun(false)
                        ApiClient.setBaseUrl(finalUrl)
                        _validationResult.value = true
                    } else if (authResponse.code == 401) {
                        _validationError.value = "Invalid password. Please check and try again."
                    } else {
                        val errorMessage =
                            when (authResponse.code) {
                                403 -> "Access forbidden. Check server configuration."
                                404 -> "API endpoint not found. Check URL path."
                                500 -> "Server error. Check server logs."
                                503 -> "Service unavailable. Try again later."
                                else -> "Server returned HTTP ${authResponse.code}"
                            }
                        _validationError.value = errorMessage
                    }
                    authResponse.close()
                } catch (e: NetworkOnMainThreadException) {
                    _validationError.value = "Network operation attempted on main thread. This is a developer error - please report this bug."
                } catch (e: UnknownHostException) {
                    _validationError.value = "Cannot resolve hostname. Check URL spelling and network connection."
                } catch (e: ConnectException) {
                    _validationError.value = "Cannot connect to server. Check if server is running and URL is correct."
                } catch (e: SocketTimeoutException) {
                    _validationError.value = "Connection timeout after 10 seconds. Server may be offline or unreachable."
                } catch (e: IOException) {
                    _validationError.value = "Network error: ${e.message ?: "Unable to communicate with server"}"
                } catch (e: Exception) {
                    _validationError.value = "Unexpected error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown cause"}"
                }
            } catch (e: IllegalArgumentException) {
                _validationError.value = "Invalid URL format: ${e.message ?: "Malformed URL"}"
            } catch (e: Exception) {
                _validationError.value = "Configuration error: ${e.javaClass.simpleName} - ${e.message ?: "Unable to process URL"}"
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun clearValidationState() {
        _validationResult.value = null
        _validationError.value = null
    }
}
