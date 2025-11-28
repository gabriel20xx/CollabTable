package com.collabtable.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.collabtable.app.data.preferences.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val viewModel = remember { ServerSetupViewModel(preferencesManager) }

    var serverUrl by remember { mutableStateOf("") }
    var serverPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isValidating by viewModel.isValidating.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    var completed by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!completed) {
            completed = true
            preferencesManager.setHasPromptedNotifications(true)
            onSetupComplete()
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // If granted, enable all; else disable all
            preferencesManager.setNotifyListAddedEnabled(granted)
            preferencesManager.setNotifyListEditedEnabled(granted)
            preferencesManager.setNotifyListRemovedEnabled(granted)
            // Also set content-updated preference consistently
            try {
                preferencesManager.setNotifyListContentUpdatedEnabled(granted)
            } catch (_: Throwable) {
                // Older builds may not have this preference; ignore
            }
            finishOnce()
        }

    fun startCompletionFlow() {
        if (completed) return

        // On Android 13+ prompt for notifications; otherwise default to enabled
        if (Build.VERSION.SDK_INT >= 33) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                preferencesManager.setNotifyListAddedEnabled(true)
                preferencesManager.setNotifyListEditedEnabled(true)
                preferencesManager.setNotifyListRemovedEnabled(true)
                try {
                    preferencesManager.setNotifyListContentUpdatedEnabled(true)
                } catch (_: Throwable) {
                }
                finishOnce()
            } else {
                // Request permission; callback will complete
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            preferencesManager.setNotifyListAddedEnabled(true)
            preferencesManager.setNotifyListEditedEnabled(true)
            preferencesManager.setNotifyListRemovedEnabled(true)
            try {
                preferencesManager.setNotifyListContentUpdatedEnabled(true)
            } catch (_: Throwable) {
            }
            finishOnce()
        }
    }

    LaunchedEffect(validationResult) {
        if (validationResult == true && !completed) {
            startCompletionFlow()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Server Setup") },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    viewModel.clearValidationState()
                },
                label = { Text("Server Hostname") },
                placeholder = { Text("example.com or 10.0.2.2:3000") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isValidating,
                isError = validationError != null,
                supportingText = {
                    when {
                        validationError != null ->
                            Text(
                                text = validationError ?: "",
                                color = MaterialTheme.colorScheme.error,
                            )
                        else -> Text("Server address (e.g. 192.168.1.5:3000)")
                    }
                },
                trailingIcon = {
                    when {
                        isValidating ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        validationResult == true ->
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        validationError != null ->
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                            )
                    }
                },
            )

            OutlinedTextField(
                value = serverPassword,
                onValueChange = {
                    serverPassword = it
                    viewModel.clearValidationState()
                },
                label = { Text("Server Password") },
                placeholder = { Text("Enter server password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isValidating,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                supportingText = { Text("Server password for authentication") },
            )

            Button(
                onClick = {
                    if (validationResult == true) {
                        startCompletionFlow()
                    } else {
                        viewModel.validateAndSaveServerUrl(serverUrl.trim(), serverPassword.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (serverUrl.isNotBlank() && serverPassword.isNotBlank() && !isValidating) || validationResult == true,
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Validating...")
                } else if (validationResult == true) {
                    Text("Continue")
                } else {
                    Text("Connect")
                }
            }

            Text(
                text = "Tip: Emulator host 10.0.2.2:3000 · Physical device uses your PC IP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
