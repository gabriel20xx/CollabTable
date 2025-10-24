package com.collabtable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.collabtable.app.data.preferences.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val viewModel = remember { ServerSetupViewModel(preferencesManager) }
    
    var serverUrl by remember { mutableStateOf("") }
    var serverPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isValidating by viewModel.isValidating.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    LaunchedEffect(validationResult) {
        if (validationResult == true) {
            // Validation successful, navigate to main screen
            onSetupComplete()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Server Setup") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Welcome text
            Text(
                text = "Welcome to CollabTable!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Please configure your server connection to get started.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Server URL input
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("example.com or 10.0.2.2:3000") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isValidating,
                isError = validationError != null,
                supportingText = {
                    when {
                        validationError != null -> Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text("Enter hostname with optional port (default: 80/443)")
                    }
                },
                trailingIcon = {
                    when {
                        isValidating -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        validationResult == true -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        validationError != null -> Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
            
            // Server Password input
            OutlinedTextField(
                value = serverPassword,
                onValueChange = { serverPassword = it },
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
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                supportingText = {
                    Text("Password required for server authentication")
                }
            )
            
            // Validate and continue button
            Button(
                onClick = { 
                    viewModel.validateAndSaveServerUrl(serverUrl.trim(), serverPassword.trim())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = serverUrl.isNotBlank() && serverPassword.isNotBlank() && !isValidating
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Validating...")
                } else {
                    Text("Validate and Continue")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tips card
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
                        text = "Connection Tips:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• For Android Emulator: 10.0.2.2:3000",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• For Physical Device: YOUR_IP:3000",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• For domains: example.com (uses port 80/443)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Port is optional, defaults to 80 (HTTP) or 443 (HTTPS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Just enter hostname:port, no need for http:// or /api/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Make sure the server is running and accessible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Note about changing later
            Text(
                text = "You can change this URL later in Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
