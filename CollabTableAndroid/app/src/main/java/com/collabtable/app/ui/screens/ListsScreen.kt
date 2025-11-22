@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.collabtable.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.core.content.ContextCompat
import com.collabtable.app.R
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.components.ConnectionStatusAction
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
    // Settings now accessed via bottom navigation; keep param for backward compat (unused)
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val database = remember { CollabTableDatabase.getDatabase(context) }
    val viewModel = remember { ListsViewModel(database, context) }
    val prefs = remember { PreferencesManager.getInstance(context) }

    val lists by viewModel.lists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // Periodically refresh 'now' to keep the relative "Updated … ago" labels current.
    // Use fine-grained updates (1s) while any table is < 60s old; otherwise back off to 30s.
    LaunchedEffect(lists) {
        while (true) {
            val currentNow = System.currentTimeMillis()
            val minAgeSec =
                lists.minOfOrNull { list ->
                    ((currentNow - list.updatedAt).coerceAtLeast(0L) / 1000L)
                } ?: Long.MAX_VALUE
            val delayMs = if (minAgeSec < 60L) 1_000L else 30_000L
            delay(delayMs)
            nowMs = System.currentTimeMillis()
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<CollabList?>(null) }
    var listToEdit by remember { mutableStateOf<CollabList?>(null) }
    var listToExport by remember { mutableStateOf<CollabList?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists)) },
                actions = {
                    val context = LocalContext.current
                    val prefsLocal = remember { PreferencesManager.getInstance(context) }

                    // Launcher to request Android 13+ notification permission on-the-fly
                    var notifPrompted by remember { mutableStateOf(false) }
                    val permissionLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                            // Mirror setup behavior: toggle all list notifications to granted state
                            prefsLocal.setNotifyListAddedEnabled(granted)
                            prefsLocal.setNotifyListEditedEnabled(granted)
                            prefsLocal.setNotifyListRemovedEnabled(granted)
                            try {
                                prefsLocal.setNotifyListContentUpdatedEnabled(granted)
                            } catch (_: Throwable) {
                            }
                        }

                    ConnectionStatusAction(
                        prefs = prefsLocal,
                        onBecameConnected = {
                            if (Build.VERSION.SDK_INT >= 33 && !notifPrompted) {
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notifPrompted = true
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                    )
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.create_list),
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
        floatingActionButton = {},
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
                var awaitingDb by remember { mutableStateOf(false) }
                var pendingOrderIds by remember { mutableStateOf<List<String>?>(null) }
                // Keep working list in sync, but when we just committed a reorder, wait until DB reflects it
                LaunchedEffect(lists) {
                    val currentIds = lists.map { it.id }
                    val pending = pendingOrderIds
                    if (awaitingDb && pending != null) {
                        if (currentIds == pending) {
                            // DB has caught up; now allow UI to exit dragging and sync lists
                            awaitingDb = false
                            dragging = false
                            working.clear()
                            working.addAll(lists)
                            pendingOrderIds = null
                        }
                        // else: still waiting for DB; do not overwrite working yet
                    } else if (!dragging) {
                        // Normal path: sync working to latest lists
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

                val reorderState =
                    rememberReorderableLazyListState(
                        onMove = { from, to ->
                            dragging = true
                            // Indices provided by reorderable correspond to draggable items only
                            working.move(from.index, to.index)
                        },
                        onDragEnd = { _, _ ->
                            // Commit and wait for DB to reflect this order before exiting drag state
                            val ids = working.map { it.id }
                            pendingOrderIds = ids
                            awaitingDb = true
                            viewModel.commitReorder(ids)
                        },
                    )

                Column(modifier = Modifier.fillMaxSize()) {
                    // Controls above the list (align with table view UX)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SortMenu(prefs = prefs)
                    }

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
                                        nowMs = nowMs,
                                        onListClick = { onNavigateToList(list.id) },
                                        onEditClick = { listToEdit = list },
                                        onDeleteClick = { listToDelete = list },
                                        onExportClick = { listToExport = list },
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
    // Export dialog (top-level) separate from create/edit/delete dialogs
    listToExport?.let { list ->
        ExportListDialog(
            list = list,
            onDismiss = { listToExport = null },
            onConfirmExport = { format ->
                if (format == "CSV") {
                    coroutineScope.launch {
                        val result = viewModel.exportListToCsv(list.id, list.name)
                        result.onSuccess { path ->
                            Toast.makeText(context, context.getString(R.string.export_success, path), Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_LONG).show()
                        }
                        listToExport = null
                    }
                } else {
                    listToExport = null
                }
            },
        )
    }
}

@Composable
fun ListItem(
    list: CollabList,
    nowMs: Long,
    onListClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    // Expand the touch target to include the vertical spacing between rows without visually changing layout.
    // We wrap the original row in a full-width Box with the same outer padding, then add a clickable
    // modifier that uses additional vertical padding but negative padding inside to keep visual spacing.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                // keep original horizontal spacing
                .padding(horizontal = 12.dp)
                // entire row (including margins) is tappable
                .clickable(onClick = onListClick)
                // expand touch area (original visual was 8.dp inside Row)
                .padding(vertical = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            // actual visual spacing remains ~8dp total (4 + 4)
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle on the far left
            if (dragHandle != null) {
                dragHandle()
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Table name and subtitle
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(text = list.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = remember(list.updatedAt, nowMs) { formatUpdatedAgo(list.updatedAt, nowMs) }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Actions on the right (retain individual click targets)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onExportClick) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.export),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val currentOrder by prefs.sortOrder.collectAsState(initial = prefs.getSortOrder())
    var expanded by remember { mutableStateOf(false) }

    val label =
        when (currentOrder) {
            PreferencesManager.SORT_UPDATED_DESC -> "Updated ↓"
            PreferencesManager.SORT_UPDATED_ASC -> "Updated ↑"
            PreferencesManager.SORT_NAME_ASC -> "Name A–Z"
            PreferencesManager.SORT_NAME_DESC -> "Name Z–A"
            else -> "Sort"
        }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Updated (newest first)") },
                onClick = {
                    prefs.setSortOrder(PreferencesManager.SORT_UPDATED_DESC)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Updated (oldest first)") },
                onClick = {
                    prefs.setSortOrder(PreferencesManager.SORT_UPDATED_ASC)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Name (A–Z)") },
                onClick = {
                    prefs.setSortOrder(PreferencesManager.SORT_NAME_ASC)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Name (Z–A)") },
                onClick = {
                    prefs.setSortOrder(PreferencesManager.SORT_NAME_DESC)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun ExportListDialog(
    list: CollabList,
    onDismiss: () -> Unit,
    onConfirmExport: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf("CSV") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_list) + ": " + list.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_format))
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(if (selectedFormat == "CSV") stringResource(R.string.format_csv) else selectedFormat)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.format_csv)) },
                            onClick = { selectedFormat = "CSV"; expanded = false },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmExport(selectedFormat) }) { Text(stringResource(R.string.export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// Format updatedAt relative to now: "Updated 5 sec/min/hour(s) ago" (days included when applicable)
private fun formatUpdatedAgo(
    updatedAt: Long,
    nowMs: Long,
): String {
    val delta = (nowMs - updatedAt).coerceAtLeast(0L)
    val seconds = delta / 1000
    return when {
        seconds < 60 -> "Updated ${seconds}s ago"
        seconds < 3600 -> {
            val minutes = seconds / 60
            "Updated ${minutes}m ago"
        }
        seconds < 86_400 -> {
            val hours = seconds / 3600
            val unit = if (hours == 1L) "hour" else "hours"
            "Updated $hours $unit ago"
        }
        else -> {
            val days = seconds / 86_400
            val unit = if (days == 1L) "day" else "days"
            "Updated $days $unit ago"
        }
    }
}
