package com.collabtable.app.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.collabtable.app.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val statusClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

private fun isEmulator(): Boolean {
    val fp = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val brand = Build.BRAND.lowercase()
    val device = Build.DEVICE.lowercase()
    val product = Build.PRODUCT.lowercase()
    return fp.contains("generic") || fp.contains("emulator") ||
        model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for") ||
        brand.startsWith("generic") || device.startsWith("generic") || product.contains("sdk")
}

private fun toHealthUrl(rawApiUrl: String): String {
    // Ensure we hit the unauthenticated /health endpoint and remap localhost on emulator
    return try {
        val uri = Uri.parse(rawApiUrl)
        val host = uri.host?.lowercase()
        val needsRemap = isEmulator() && (host == "localhost" || host == "127.0.0.1" || host == "host.docker.internal")
        val scheme = uri.scheme ?: "http"
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val base = if (needsRemap) "$scheme://10.0.2.2$portPart" else "$scheme://${uri.host ?: ""}$portPart"
        val path = (uri.encodedPath ?: "/").trimEnd('/')
        val healthPath = if (path.endsWith("/api") || path.endsWith("/api/")) "/health" else "/health"
        base + healthPath
    } catch (e: Exception) {
        rawApiUrl.replace("/api/", "/health").replace("/api", "/health")
    }
}

@Composable
fun ConnectionStatusAction(
    prefs: PreferencesManager,
    modifier: Modifier = Modifier,
    showLatency: Boolean = true,
) {
    var ok by remember { mutableStateOf<Boolean?>(null) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }

    val intervalMs by prefs.syncPollIntervalMs.collectAsState()

    // Periodic health check aligned to polling interval
    LaunchedEffect(intervalMs) {
        while (true) {
            try {
                val healthUrl = toHealthUrl(prefs.getServerUrl())
                val req = Request.Builder().url(healthUrl).get().build()
                val start = System.nanoTime()
                val resp = withContext(Dispatchers.IO) { statusClient.newCall(req).execute() }
                val tookMs = (System.nanoTime() - start) / 1_000_000
                if (resp.isSuccessful) {
                    ok = true
                    latencyMs = tookMs
                } else {
                    ok = false
                }
                resp.close()
            } catch (e: Exception) {
                ok = false
            }
            delay(intervalMs)
        }
    }

    val dotColor: Color = when (ok) {
        true -> Color(0xFF2E7D32) // green
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showLatency && ok == true && latencyMs != null) {
            Text(
                text = "${latencyMs} ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(dotColor, shape = androidx.compose.foundation.shape.CircleShape),
        )
    }
}
