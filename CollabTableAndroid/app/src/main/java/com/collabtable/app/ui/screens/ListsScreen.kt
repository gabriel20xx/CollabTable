@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.collabtable.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.collabtable.app.R
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.preferences.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onNavigateToList: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val database = remember { CollabTableDatabase.getDatabase(context) }
    val viewModel = remember { ListsViewModel(database, context) }
    val prefs = remember { PreferencesManager.getInstance(context) }

    val lists by viewModel.lists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<CollabList?>(null) }
    var listToEdit by remember { mutableStateOf<CollabList?>(null) }

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
                    SortMenu(prefs = prefs)
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_list))
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (isLoading && lists.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Syncing with server...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (lists.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.no_lists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tap + to create your first table",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Maintain a working list as a mutable state list for smoother edge animations
                val working = remember { androidx.compose.runtime.mutableStateListOf<CollabList>() }
                var dragging by remember { mutableStateOf(false) }
                // Keep working list in sync when not dragging
                LaunchedEffect(lists, dragging) {
                    if (!dragging) {
                        working.clear()
                        working.addAll(lists)
                    }
                }

                fun MutableList<CollabList>.move(
                    from: Int,
                    to: Int,
                ) {
                    if (from == to) return
                    if (isEmpty() || from !in indices) return
                    add(to.coerceIn(0, size), removeAt(from))
                }

                val scope = rememberCoroutineScope()
                val reorderState =
                    rememberReorderableLazyListState(
                        onMove = { from, to ->
                            dragging = true
                            // Indices provided by reorderable correspond to draggable items only
                            working.move(from.index, to.index)
                        },
                        onDragEnd = { _, _ ->
                            // Commit first, then keep dragging=true briefly so the UI can animate smoothly
                            viewModel.commitReorder(working.map { it.id })
                            scope.launch {
                                delay(150)
                                dragging = false
                            }
                        },
                    )

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .reorderable(reorderState),
                    state = reorderState.listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(working, key = { _, it -> it.id }) { _, list ->
                        ReorderableItem(reorderState, key = list.id) { _ ->
                            Box(modifier = Modifier.animateItemPlacement()) {
                                ListItem(
                                    list = list,
                                    onListClick = { onNavigateToList(list.id) },
                                    onEditClick = { listToEdit = list },
                                    onDeleteClick = { listToDelete = list },
                                    dragHandle = {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Reorder",
                                            modifier = Modifier.detectReorder(reorderState),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                            }
                        }
                    }
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
            },
        )
    }

    listToEdit?.let { list ->
        RenameListDialog(
            currentName = list.name,
            onDismiss = { listToEdit = null },
            onRename = { newName ->
                viewModel.renameList(list.id, newName)
                listToEdit = null
            },
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
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun ListItem(
    list: CollabList,
    onListClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle on the far left
        if (dragHandle != null) {
            dragHandle()
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Table name centered/left and clickable
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onListClick),
        ) {
            Text(text = list.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Actions on the right
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
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
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (listName.isNotBlank()) onCreate(listName.trim()) },
                enabled = listName.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RenameListDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var listName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Table") },
        text = {
            OutlinedTextField(
                value = listName,
                onValueChange = { listName = it },
                label = { Text(stringResource(R.string.list_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (listName.isNotBlank()) onRename(listName.trim()) },
                enabled = listName.isNotBlank() && listName.trim() != currentName,
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SortMenu(prefs: PreferencesManager) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val currentOrder by prefs.sortOrder.collectAsState(initial = prefs.getSortOrder())

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = "Sort")
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            @Composable
            fun ItemOption(
                label: String,
                value: String,
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label)
                            if (currentOrder == value) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text("✓")
                            }
                        }
                    },
                    onClick = {
                        prefs.setSortOrder(value)
                        expanded = false
                    },
                )
            }

            ItemOption("Updated (newest first)", PreferencesManager.SORT_UPDATED_DESC)
            ItemOption("Updated (oldest first)", PreferencesManager.SORT_UPDATED_ASC)
            ItemOption("Name (A–Z)", PreferencesManager.SORT_NAME_ASC)
            ItemOption("Name (Z–A)", PreferencesManager.SORT_NAME_DESC)
        }
    }
}
