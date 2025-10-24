package com.collabtable.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabtable.app.utils.LogEntry
import com.collabtable.app.utils.LogLevel
import com.collabtable.app.utils.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit
) {
    val logs by Logger.logs.collectAsState()
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { Logger.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "No logs yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF1E1E1E)),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(logs) { log ->
                    LogItem(log)
                }
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    val color = when (log.level) {
        LogLevel.DEBUG -> Color(0xFF808080)
        LogLevel.INFO -> Color(0xFF4FC3F7)
        LogLevel.WARN -> Color(0xFFFFA726)
        LogLevel.ERROR -> Color(0xFFEF5350)
    }
    
    Text(
        text = log.toFormattedString(),
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
