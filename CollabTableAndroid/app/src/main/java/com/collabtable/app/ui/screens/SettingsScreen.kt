package com.collabtable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.collabtable.app.R
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.data.repository.SyncRepository
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLeaveServer: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val syncRepository = remember { SyncRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val serverUrl = preferencesManager.getServerUrl()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Server Connection",
                style = MaterialTheme.typography.titleMedium
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connected to:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { showLeaveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLeaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (isLeaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leaving...")
                } else {
                    Text("Leave Server")
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About Leave Server:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Disconnects from the current server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Clears stored server URL and password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Deletes all local data (lists, fields, items)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Returns to server setup screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        if (showLeaveDialog) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Leave Server?") },
                text = { 
                    Text("All your data will be synced to the server before disconnecting. After leaving, all local data will be deleted and you'll need to set up a new connection. This action cannot be undone.")
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
                                    
                                    // Clear preferences
                                    preferencesManager.setServerUrl("")
                                    preferencesManager.setServerPassword("")
                                    preferencesManager.setIsFirstRun(true)
                                    
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
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Leave Server")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
