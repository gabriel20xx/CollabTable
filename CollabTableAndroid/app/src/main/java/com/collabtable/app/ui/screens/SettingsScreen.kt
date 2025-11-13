package com.collabtable.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.collabtable.app.R
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.data.repository.SyncRepository
import com.collabtable.app.ui.components.ConnectionStatusAction
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val snackbarHostState = remember { SnackbarHostState() }

    val serverUrl = preferencesManager.getServerUrl()
    val themeMode by preferencesManager.themeMode.collectAsState()
    val dynamicColor by preferencesManager.dynamicColor.collectAsState()
    val amoledDark by preferencesManager.amoledDark.collectAsState()
    val notifyListAdded by preferencesManager.notifyListAdded.collectAsState()
    val notifyListEdited by preferencesManager.notifyListEdited.collectAsState()
    val notifyListRemoved by preferencesManager.notifyListRemoved.collectAsState()
    val notifyListContentUpdated by preferencesManager.notifyListContentUpdated.collectAsState()
    val displayUrl = remember(serverUrl) { formatServerUrlForDisplay(serverUrl) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    val syncIntervalMs by preferencesManager.syncPollIntervalMs.collectAsState()
    var syncIntervalInput by remember(syncIntervalMs) { mutableStateOf(syncIntervalMs.toString()) }
    var syncIntervalError by remember { mutableStateOf<String?>(null) }
    // Authentication UI removed

    // Permission launcher for Android 13+ notification permission
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingPermissionAction?.invoke()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Notification permission denied. Enable it in system settings to turn this on.",
                    )
                }
            }
            pendingPermissionAction = null
        }

    fun ensureNotificationPermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onGranted()
            return
        }
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted()
        } else {
            pendingPermissionAction = onGranted
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Removed test connectivity logic; the connection indicator is shown via ConnectionStatusAction

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    ConnectionStatusAction(prefs = preferencesManager)
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.server_connection),
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
                        text = stringResource(R.string.connected_to),
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

            // Rely on Column's verticalArrangement for consistent spacing

            // Authentication section removed

            Text(
                text = stringResource(R.string.appearance),
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
                    label = { Text(stringResource(R.string.theme_system)) },
                    leadingIcon = { Icon(Icons.Filled.SettingsBrightness, contentDescription = null) },
                )
                FilterChip(
                    selected = themeMode == PreferencesManager.THEME_MODE_LIGHT,
                    onClick = { preferencesManager.setThemeMode(PreferencesManager.THEME_MODE_LIGHT) },
                    label = { Text(stringResource(R.string.theme_light)) },
                    leadingIcon = { Icon(Icons.Filled.Brightness7, contentDescription = null) },
                )
                FilterChip(
                    selected = themeMode == PreferencesManager.THEME_MODE_DARK,
                    onClick = { preferencesManager.setThemeMode(PreferencesManager.THEME_MODE_DARK) },
                    label = { Text(stringResource(R.string.theme_dark)) },
                    leadingIcon = { Icon(Icons.Filled.Brightness4, contentDescription = null) },
                )
            }

            // Dynamic color toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.use_material3_colors))
                    Text(
                        text = stringResource(R.string.dynamic_colors_desc),
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
                    Text(stringResource(R.string.amoled_dark))
                    Text(
                        text = stringResource(R.string.amoled_dark_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = amoledDark, onCheckedChange = { preferencesManager.setAmoledDarkEnabled(it) })
            }

            // Sync settings
            Text(
                text = stringResource(R.string.sync),
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
                        text = stringResource(R.string.http_polling_interval_ms),
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
                            Text(stringResource(R.string.apply))
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
                            text = stringResource(R.string.current_interval, syncIntervalMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Notifications section (moved inside the main scroll column so it doesn't overlay other content)
            Text(
                text = stringResource(R.string.notifications),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notify_list_added_title))
                            Text(
                                text = stringResource(R.string.notify_list_added_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = notifyListAdded,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    ensureNotificationPermission {
                                        preferencesManager.setNotifyListAddedEnabled(true)
                                    }
                                } else {
                                    preferencesManager.setNotifyListAddedEnabled(false)
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notify_list_edited_title))
                            Text(
                                text = stringResource(R.string.notify_list_edited_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = notifyListEdited,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    ensureNotificationPermission {
                                        preferencesManager.setNotifyListEditedEnabled(true)
                                    }
                                } else {
                                    preferencesManager.setNotifyListEditedEnabled(false)
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notify_list_content_title))
                            Text(
                                text = stringResource(R.string.notify_list_content_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = notifyListContentUpdated,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    ensureNotificationPermission {
                                        preferencesManager.setNotifyListContentUpdatedEnabled(true)
                                    }
                                } else {
                                    preferencesManager.setNotifyListContentUpdatedEnabled(false)
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.notify_list_removed_title))
                            Text(
                                text = stringResource(R.string.notify_list_removed_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = notifyListRemoved,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    ensureNotificationPermission {
                                        preferencesManager.setNotifyListRemovedEnabled(true)
                                    }
                                } else {
                                    preferencesManager.setNotifyListRemovedEnabled(false)
                                }
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.notifications_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Diagnostics / Logs
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                        Text(
                        text = "View application logs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onNavigateToLogs, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.open_logs))
                    }
                }
            }

            // Leave Server section
            Text(
                text = stringResource(R.string.leave_server_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Warning/info text appears above the action button
                    Text(
                        text = stringResource(R.string.leaving_removes_local_data),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.disconnects_and_clears_db),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.clears_server_settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.deletes_all_local),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.returns_to_setup),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Action button placed after the information for better UX
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
                            Text(stringResource(R.string.leaving_progress))
                        } else {
                            Text(stringResource(R.string.leave_server))
                        }
                    }
                }
            }
        }

        if (showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text(stringResource(R.string.leave_server_dialog_title)) },
                text = {
                    Text(stringResource(R.string.leave_server_dialog_body),
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
                        Text(stringResource(R.string.leave_server))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    // Test connectivity removed; connection status is shown in the top app bar indicator
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
