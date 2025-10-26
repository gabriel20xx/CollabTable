package com.collabtable.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.collabtable.app.ui.components.ConnectionStatusAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.collabtable.app.R
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.data.repository.SyncRepository
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.os.Build
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLeaveServer: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val syncRepository = remember { SyncRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    val serverUrl = preferencesManager.getServerUrl()
    val themeMode by preferencesManager.themeMode.collectAsState()
    val dynamicColor by preferencesManager.dynamicColor.collectAsState()
    val amoledDark by preferencesManager.amoledDark.collectAsState()
    val displayUrl = remember(serverUrl) { formatServerUrlForDisplay(serverUrl) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    val syncIntervalMs by preferencesManager.syncPollIntervalMs.collectAsState()
    var syncIntervalInput by remember(syncIntervalMs) { mutableStateOf(syncIntervalMs.toString()) }
    var syncIntervalError by remember { mutableStateOf<String?>(null) }

    // Test connection state
    var isTesting by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var lastLatencyMs by remember { mutableStateOf<Long?>(null) }

    val testClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fp.contains("generic") || fp.contains("emulator") ||
            model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for") ||
            brand.startsWith("generic") || device.startsWith("generic") || product.contains("sdk")
    }

    fun toHealthUrl(rawApiUrl: String): String {
        // Ensure we hit the unauthenticated /health endpoint
        var url = rawApiUrl
        // Emu remap for local hosts
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase()
            val needsRemap = isEmulator() && (host == "localhost" || host == "127.0.0.1" || host == "host.docker.internal")
            val scheme = uri.scheme ?: "http"
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            val base = if (needsRemap) "$scheme://10.0.2.2$portPart" else "$scheme://${uri.host ?: ""}$portPart"
            // Replace any /api or /api/ segment at end with /health
            val path = (uri.encodedPath ?: "/").trimEnd('/')
            val healthPath = if (path.endsWith("/api")) "/health" else if (path.endsWith("/api/")) "/health" else "/health"
            return base + healthPath
        } catch (e: Exception) {
            // Fallback: naive replace
            return rawApiUrl.replace("/api/", "/health").replace("/api", "/health")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ConnectionStatusAction(prefs = preferencesManager)
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(Icons.Default.List, contentDescription = "Logs")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Server Connection",
                style = MaterialTheme.typography.titleMedium,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Connected to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = displayUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
            )

            // Theme mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = themeMode == PreferencesManager.THEME_MODE_SYSTEM,
                    onClick = { preferencesManager.setThemeMode(PreferencesManager.THEME_MODE_SYSTEM) },
                    label = { Text("System") },
                    leadingIcon = { Icon(Icons.Default.SettingsBrightness, contentDescription = null) },
                )
                FilterChip(
                    selected = themeMode == PreferencesManager.THEME_MODE_LIGHT,
                    onClick = { preferencesManager.setThemeMode(PreferencesManager.THEME_MODE_LIGHT) },
                    label = { Text("Light") },
                    leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null) },
                )
                FilterChip(
                    selected = themeMode == PreferencesManager.THEME_MODE_DARK,
                    onClick = { preferencesManager.setThemeMode(PreferencesManager.THEME_MODE_DARK) },
                    label = { Text("Dark") },
                    leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                )
            }

            // Dynamic color toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Material 3 colors")
                    Text(
                        text = "Dynamic colors on supported devices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = { preferencesManager.setDynamicColorEnabled(it) })
            }

            // AMOLED dark toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AMOLED dark")
                    Text(
                        text = "Pure black background in dark mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = amoledDark, onCheckedChange = { preferencesManager.setAmoledDarkEnabled(it) })
            }

            Button(
                onClick = { showLeaveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLeaving,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                if (isLeaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leaving...")
                } else {
                    Text("Leave Server")
                }
            }

            // Sync settings
            Text(
                text = "Sync",
                style = MaterialTheme.typography.titleMedium,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "HTTP polling interval (ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = syncIntervalInput,
                            onValueChange = {
                                syncIntervalInput = it.filter { ch -> ch.isDigit() }
                                syncIntervalError = null
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("250") },
                        )
                        Button(onClick = {
                            val parsed = syncIntervalInput.toLongOrNull()
                            if (parsed == null) {
                                syncIntervalError = "Enter a valid number"
                            } else {
                                // Bounds are enforced in setter
                                preferencesManager.setSyncPollIntervalMs(parsed)
                                syncIntervalError = null
                            }
                        }) {
                            Text("Apply")
                        }
                    }
                    if (syncIntervalError != null) {
                        Text(
                            text = syncIntervalError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Text(
                            text = "Current: $syncIntervalMs ms (min 250, max 600000)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Connection test
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Server connectivity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testOk = null
                                testMessage = null
                                lastLatencyMs = null
                                coroutineScope.launch {
                                    val rawApi = preferencesManager.getServerUrl()
                                    val healthUrl = toHealthUrl(rawApi)
                                    val req = Request.Builder().url(healthUrl).get().build()
                                    val start = System.nanoTime()
                                    try {
                                        val resp = withContext(Dispatchers.IO) { testClient.newCall(req).execute() }
                                        val tookMs = (System.nanoTime() - start) / 1_000_000
                                        if (resp.isSuccessful) {
                                            testOk = true
                                            lastLatencyMs = tookMs
                                            testMessage = "Reachable (${tookMs} ms)"
                                        } else {
                                            testOk = false
                                            testMessage = "HTTP ${resp.code}"
                                        }
                                        resp.close()
                                    } catch (e: Exception) {
                                        testOk = false
                                        testMessage = e.message ?: "Connection error"
                                    } finally {
                                        isTesting = false
                                    }
                                }
                            },
                            enabled = !isTesting,
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing…")
                            } else {
                                Text("Test connection")
                            }
                        }
                        when (testOk) {
                            true -> Text(
                                text = testMessage ?: "Reachable",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            false -> Text(
                                text = testMessage ?: "Not reachable",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            null -> {}
                        }
                    }
                    if (testOk == false) {
                        Text(
                            text = "Tip: On emulator use http://10.0.2.2:PORT/api/. On a device use your PC's LAN IP.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Leaving removes local data",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Disconnects and clears local database",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Clears stored server URL and password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Deletes all local data (tables, fields, items)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Returns to server setup screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        if (showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Leave Server?") },
                text = {
                    Text(
                        "All your data will be synced to the server before disconnecting. " +
                            "After leaving, all local data will be deleted and you'll need to set up a new connection. " +
                            "This action cannot be undone.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isLeaving = true
                            coroutineScope.launch {
                                try {
                                    Logger.i("Settings", "Performing final sync before leaving server")

                                    // Perform final sync to ensure all data is uploaded
                                    withContext(Dispatchers.IO) {
                                        syncRepository.performSync()
                                    }

                                    Logger.i("Settings", "Final sync completed, clearing local data")

                                    // Clear database in background
                                    withContext(Dispatchers.IO) {
                                        CollabTableDatabase.clearDatabase(context)
                                    }

                                    // Clear preferences (URL, password, last sync)
                                    preferencesManager.clearServerSettings()
                                    preferencesManager.setIsFirstRun(true)
                                    // Clear in-memory logs as part of local data
                                    Logger.clear()
                                    // Reset API client base URL to current preference (default)
                                    ApiClient.setBaseUrl(preferencesManager.getServerUrl())

                                    Logger.i("Settings", "Left server successfully")

                                    showLeaveDialog = false
                                    onLeaveServer()
                                } catch (e: Exception) {
                                    // Handle error if needed
                                    Logger.e("Settings", "Error leaving server", e)
                                    isLeaving = false
                                }
                            }
                        },
                        enabled = !isLeaving,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Leave Server")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }

    // Perform a silent health check when entering the screen to set the indicator
    LaunchedEffect(Unit) {
        try {
            val rawApi = preferencesManager.getServerUrl()
            val healthUrl = toHealthUrl(rawApi)
            val req = Request.Builder().url(healthUrl).get().build()
            val start = System.nanoTime()
            val resp = withContext(Dispatchers.IO) { testClient.newCall(req).execute() }
            val tookMs = (System.nanoTime() - start) / 1_000_000
            if (resp.isSuccessful) {
                testOk = true
                lastLatencyMs = tookMs
                testMessage = "Reachable (${tookMs} ms)"
            } else {
                testOk = false
                testMessage = "HTTP ${resp.code}"
            }
            resp.close()
        } catch (e: Exception) {
            testOk = false
            testMessage = e.message ?: "Connection error"
        }
    }
}
private fun formatServerUrlForDisplay(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return ""
    // Remove scheme
    s = s.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
    // Remove trailing /api or /api/
    s = s.replace(Regex("/api/?$", RegexOption.IGNORE_CASE), "")
    // Trim trailing /
    s = s.trimEnd('/')
    // Split authority and path (in case any path remains)
    val firstSlash = s.indexOf('/')
    val authority = if (firstSlash >= 0) s.substring(0, firstSlash) else s
    val rest = if (firstSlash >= 0) s.substring(firstSlash) else ""
    // Remove default ports from authority
    val authorityStripped =
        when {
            authority.endsWith(":80") -> authority.removeSuffix(":80")
            authority.endsWith(":443") -> authority.removeSuffix(":443")
            else -> authority
        }
    return authorityStripped + rest
}
