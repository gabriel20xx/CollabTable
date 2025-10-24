package com.collabtable.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.collabtable.app.R
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onNavigateToList: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { CollabTableDatabase.getDatabase(context) }
    val viewModel = remember { ListsViewModel(database, context) }
    
    val lists by viewModel.lists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<CollabList?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists)) },
                actions = {
                    IconButton(onClick = { viewModel.manualSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(Icons.Default.List, contentDescription = "Logs")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_list))
            }
        }
    ) { padding ->
        if (lists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_lists),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lists, key = { it.id }) { list ->
                    ListItem(
                        list = list,
                        onListClick = { onNavigateToList(list.id) },
                        onDeleteClick = { listToDelete = list }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createList(name)
                showCreateDialog = false
            }
        )
    }

    listToDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text(stringResource(R.string.delete_list)) },
            text = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteList(list.id)
                        listToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ListItem(
    list: CollabList,
    onListClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onListClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(list.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var listName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_list)) },
        text = {
            OutlinedTextField(
                value = listName,
                onValueChange = { listName = it },
                label = { Text(stringResource(R.string.list_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (listName.isNotBlank()) onCreate(listName.trim()) },
                enabled = listName.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
