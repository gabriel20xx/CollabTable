@file:Suppress("ktlint:standard:no-wildcard-imports")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.collabtable.app.ui.screens
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.collabtable.app.R
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.Field
import com.collabtable.app.data.model.ItemWithValues
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.components.ConnectionStatusAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListDetailScreen(
    listId: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val database = remember { CollabTableDatabase.getDatabase(context) }
    val viewModel = remember { ListDetailViewModel(database, listId, context) }
    val prefs = remember { PreferencesManager.getInstance(context) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerTextStyle = MaterialTheme.typography.labelLarge
    val bodyTextStyle = MaterialTheme.typography.bodyMedium

    val list by viewModel.list.collectAsState()
    val fields by viewModel.fields.collectAsState()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Use derivedStateOf to create stable references
    val stableFields by remember { derivedStateOf { fields } }
    val stableItems by remember { derivedStateOf { items } }
    var itemToEdit by remember { mutableStateOf<ItemWithValues?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }
    var showManageColumnsDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    // Flag to trigger scroll to bottom after adding an item
    var pendingScrollToBottom by remember { mutableStateOf(false) }

    // Filter/Sort state
    var sortField by remember { mutableStateOf<Field?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    var groupByField by remember { mutableStateOf<Field?>(null) }
    var filterField by remember { mutableStateOf<Field?>(null) }
    var filterValue by remember { mutableStateOf("") }

    // Shared scroll state for synchronized horizontal scrolling
    val horizontalScrollState = rememberScrollState()
    // Removed top-level scope; local scopes are used where needed

    // Field widths state (resizable columns)
    val fieldWidths = remember { mutableStateMapOf<String, Dp>() }

    // Per-column content alignment: "start" | "center" | "end"
    val columnAlignments = remember { mutableStateMapOf<String, String>() }

    // Initialize field widths (load persisted per-list widths, fallback to default)
    LaunchedEffect(stableFields) {
        // Load saved widths for this listId
        val saved = prefs.getColumnWidths(listId)
        stableFields.forEach { field ->
            val savedWidth = saved[field.id]?.coerceAtLeast(100f)
            val widthDp = (savedWidth ?: 150f).dp
            fieldWidths[field.id] = widthDp
        }
        // Load saved alignments for this listId
        val savedAlign = prefs.getColumnAlignments(listId)
        stableFields.forEach { field ->
            val a = savedAlign[field.id]?.lowercase() ?: "start"
            columnAlignments[field.id] =
                when (a) {
                    "center" -> "center"
                    "end", "right" -> "end"
                    else -> "start"
                }
        }
    }

    // Scaffold with top bar and FAB
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.clickable { showRenameListDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(list?.name ?: "")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val localCtx = LocalContext.current
                    val localPrefs = remember { PreferencesManager.getInstance(localCtx) }
                    ConnectionStatusAction(prefs = localPrefs)
                    IconButton(onClick = { showAddItemDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_item))
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
        // Apply filtering, sorting and grouping off the main thread and memoize the result
        val transformed by produceState(
            initialValue = TransformedItems(emptyList(), mapOf("_all" to emptyList())),
            stableItems,
            sortField,
            sortAscending,
            groupByField,
            filterField,
            filterValue,
        ) {
            value =
                withContext(Dispatchers.Default) {
                    transformItems(
                        items = stableItems,
                        sortField = sortField,
                        sortAscending = sortAscending,
                        groupByField = groupByField,
                        filterField = filterField,
                        filterValue = filterValue,
                    )
                }
        }

        val processedItems = transformed.processed
        val groupedItems = transformed.grouped
        // Root container to allow loading overlay
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (stableFields.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No fields yet. Add fields to get started!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showManageColumnsDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_field))
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                // Filter/Sort/Group Controls - Always visible above table
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Sort button
                    FilterChip(
                        selected = sortField != null,
                        onClick = { showSortDialog = true },
                        label = {
                            Text(
                                if (sortField != null) {
                                    "Sort: ${sortField!!.name} ${if (sortAscending) "↑" else "↓"}"
                                } else {
                                    "Sort"
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon =
                            if (sortField != null) {
                                {
                                    IconButton(
                                        onClick = {
                                            sortField = null
                                            sortAscending = true
                                        },
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )

                    // Group button
                    FilterChip(
                        selected = groupByField != null,
                        onClick = { showGroupDialog = true },
                        label = {
                            Text(
                                if (groupByField != null) {
                                    "Group: ${groupByField!!.name}"
                                } else {
                                    "Group"
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon =
                            if (groupByField != null) {
                                {
                                    IconButton(
                                        onClick = { groupByField = null },
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )

                    // Filter button
                    FilterChip(
                        selected = filterField != null,
                        onClick = { showFilterDialog = true },
                        label = {
                            Text(
                                if (filterField != null) {
                                    "Filter: ${filterField!!.name}"
                                } else {
                                    "Filter"
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon =
                            if (filterField != null) {
                                {
                                    IconButton(
                                        onClick = {
                                            filterField = null
                                            filterValue = ""
                                        },
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )

                    // Auto-resize button (chip style)
                    // Auto-fit cache keyed by fieldId with a compact signature based on timestamps and counts
                    val autoFitCache = remember { mutableStateMapOf<String, Pair<AutoFitSignature, Float>>() }
                    FilterChip(
                        selected = false,
                        onClick = {
                            stableFields.forEach { field ->
                                // Build a compact signature: field name, field.updatedAt, item count, max updatedAt among values
                                var maxValUpdated = 0L
                                stableItems.forEach { item ->
                                    val v = item.values.find { it.fieldId == field.id }
                                    if (v != null && v.updatedAt > maxValUpdated) maxValUpdated = v.updatedAt
                                }
                                val signature =
                                    AutoFitSignature(
                                        name = field.name,
                                        fieldUpdatedAt = field.updatedAt,
                                        itemCount = stableItems.size,
                                        maxValueUpdatedAt = maxValUpdated,
                                    )

                                val cached = autoFitCache[field.id]
                                if (cached != null && cached.first == signature) {
                                    // Use cached width
                                    fieldWidths[field.id] = cached.second.dp
                                    return@forEach
                                }

                                // Measure header and max content width
                                val headerPx =
                                    textMeasurer
                                        .measure(AnnotatedString(field.name), style = headerTextStyle)
                                        .size.width
                                        .toFloat()
                                var maxContentPx = 0f
                                stableItems.forEach { itemWithValues ->
                                    val v = itemWithValues.values.find { it.fieldId == field.id }?.value
                                    val display = getDisplayTextForMeasure(field, v)
                                    if (display.isNotEmpty()) {
                                        val w =
                                            textMeasurer
                                                .measure(AnnotatedString(display), style = bodyTextStyle)
                                                .size.width
                                                .toFloat()
                                        if (w > maxContentPx) maxContentPx = w
                                    }
                                }
                                val widthDpValue =
                                    with(density) {
                                        val headerDp = headerPx.toDp() + 12.dp + 12.dp + 24.dp + 2.dp
                                        val contentDp = maxContentPx.toDp() + 8.dp + 8.dp + 2.dp
                                        val base = maxOf(headerDp, contentDp, 100.dp)
                                        (base + 6.dp).value
                                    }
                                fieldWidths[field.id] = widthDpValue.dp
                                autoFitCache[field.id] = signature to widthDpValue
                            }
                            prefs.setColumnWidths(listId, fieldWidths.mapValues { it.value.value })
                        },
                        label = { Text("Auto-fit") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FitScreen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }

                // Header will be rendered as a stickyHeader inside the LazyColumn below

                // Items list with synchronized scrolling
                if (stableItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_items),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (processedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No items match the current filter",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                filterField = null
                                filterValue = ""
                            }) {
                                Text("Clear Filter")
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    // If a new item was just added, scroll to bottom after composition
                    LaunchedEffect(processedItems.size, pendingScrollToBottom) {
                        if (pendingScrollToBottom) {
                            // Scroll to the last real item index
                            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            try {
                                listState.animateScrollToItem(lastIndex)
                            } catch (_: Exception) {
                                listState.scrollToItem(lastIndex)
                            }
                            pendingScrollToBottom = false
                        }
                    }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState),
                    ) {
                        // Fixed header (does not participate in vertical scroll)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            stableFields.forEach { field ->
                                key(field.id) {
                                    FieldHeader(
                                        field = field,
                                        width = fieldWidths[field.id] ?: 150.dp,
                                        onWidthChange = { delta ->
                                            val currentWidth = fieldWidths[field.id] ?: 150.dp
                                            val newWidth = (currentWidth.value + delta).coerceAtLeast(100f)
                                            fieldWidths[field.id] = newWidth.dp
                                            prefs.setColumnWidths(
                                                listId,
                                                fieldWidths.mapValues { it.value.value },
                                            )
                                        },
                                        scrollState = horizontalScrollState,
                                        isLast = (field.id == stableFields.lastOrNull()?.id),
                                        onHeaderClick = { showManageColumnsDialog = true },
                                        alignment = columnAlignments[field.id] ?: "start",
                                        onAlignmentChange = { newAlign ->
                                            columnAlignments[field.id] = newAlign
                                            prefs.setColumnAlignments(listId, columnAlignments.toMap())
                                        },
                                    )
                                }
                            }

                            // Loading overlay only while nothing is available to render yet
                            if (isLoading && stableFields.isEmpty() && stableItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            }
                        }

                        // Items list below the fixed header
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            groupedItems.entries.forEach { (_, groupItems) ->
                                // Show items in the group
                                items(
                                    items = groupItems,
                                    key = { it.item.id },
                                    contentType = { "row" },
                                ) { itemWithValues ->
                                    ItemRow(
                                        fields = stableFields,
                                        fieldWidths = fieldWidths,
                                        fieldAlignments = columnAlignments,
                                        itemWithValues = itemWithValues,
                                        onClick = { itemToEdit = itemWithValues },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManageColumnsDialog) {
        ManageColumnsDialog(
            fields = stableFields,
            onDismiss = {
                showManageColumnsDialog = false
                // Refresh alignments from preferences in case they changed while editing columns
                val savedAlign = prefs.getColumnAlignments(listId)
                stableFields.forEach { field ->
                    val a = savedAlign[field.id]?.lowercase() ?: columnAlignments[field.id] ?: "start"
                    columnAlignments[field.id] =
                        when (a) {
                            "center" -> "center"
                            "end", "right" -> "end"
                            else -> "start"
                        }
                }
            },
            onAddField = { name, fieldType, fieldOptions ->
                viewModel.addField(name, fieldType, fieldOptions)
            },
            onUpdateField = { fieldId, name, fieldType, fieldOptions ->
                viewModel.updateField(fieldId, name, fieldType, fieldOptions)
            },
            onDeleteField = { fieldId ->
                viewModel.deleteField(fieldId)
            },
            onReorderFields = { reorderedFields ->
                viewModel.reorderFields(reorderedFields)
            },
        )
    }

    if (showAddItemDialog) {
        AddItemDialog(
            fields = stableFields,
            onDismiss = { showAddItemDialog = false },
            onAdd = { fieldValues ->
                viewModel.addItemWithValues(fieldValues)
                showAddItemDialog = false
                pendingScrollToBottom = true
            },
        )
    }

    // Sort Dialog
    if (showSortDialog) {
        SortDialog(
            fields = stableFields,
            currentSortField = sortField,
            currentSortAscending = sortAscending,
            onDismiss = { showSortDialog = false },
            onApply = { newSortField, newSortAscending ->
                sortField = newSortField
                sortAscending = newSortAscending
                showSortDialog = false
            },
        )
    }

    // Group Dialog
    if (showGroupDialog) {
        GroupDialog(
            fields = stableFields,
            currentGroupByField = groupByField,
            onDismiss = { showGroupDialog = false },
            onApply = { newGroupByField ->
                groupByField = newGroupByField
                showGroupDialog = false
            },
        )
    }

    // Filter Dialog
    if (showFilterDialog) {
        FilterDialog(
            fields = stableFields,
            currentFilterField = filterField,
            currentFilterValue = filterValue,
            onDismiss = { showFilterDialog = false },
            onApply = { newFilterField, newFilterValue ->
                filterField = newFilterField
                filterValue = newFilterValue
                showFilterDialog = false
            },
        )
    }

    // Rename List Dialog
    if (showRenameListDialog) {
        list?.let { currentList ->
            RenameListDialog(
                currentName = currentList.name,
                onDismiss = { showRenameListDialog = false },
                onRename = { newName ->
                    viewModel.renameList(newName)
                    showRenameListDialog = false
                },
            )
        }
    }

    itemToEdit?.let { itemWithValues ->
        EditItemDialog(
            fields = stableFields,
            itemWithValues = itemWithValues,
            onDismiss = { itemToEdit = null },
            onUpdate = { fieldValues ->
                fieldValues.forEach { (valueId, newValue) ->
                    viewModel.updateItemValue(valueId, newValue)
                }
                itemToEdit = null
            },
            onDelete = {
                viewModel.deleteItem(itemWithValues.item.id)
                itemToEdit = null
            },
        )
    }
    // END ListDetailScreen composable
}

// Extra closing brace to properly terminate ListDetailScreen; previous edits removed one
}

// Pure helpers used for transforming and grouping items
private data class TransformedItems(
    val processed: List<ItemWithValues>,
    val grouped: Map<String, List<ItemWithValues>>,
)

private fun valueFor(
    item: ItemWithValues,
    field: Field?,
): String {
    if (field == null) return ""
    return item.values.find { it.fieldId == field.id }?.value ?: ""
}

private fun transformItems(
    items: List<ItemWithValues>,
    sortField: Field?,
    sortAscending: Boolean,
    groupByField: Field?,
    filterField: Field?,
    filterValue: String,
): TransformedItems {
    val filtered: List<ItemWithValues> =
        if (filterField != null && filterValue.isNotBlank()) {
            val needle = filterValue.lowercase(Locale.getDefault())
            items.filter { item -> valueFor(item, filterField).lowercase(Locale.getDefault()).contains(needle) }
        } else {
            items
        }

    val sorted: List<ItemWithValues> =
        if (sortField != null) {
            val base = filtered.sortedWith(compareBy { item -> valueFor(item, sortField) })
            if (sortAscending) base else base.asReversed()
        } else {
            filtered.sortedBy { it.item.createdAt }
        }

    val grouped: Map<String, List<ItemWithValues>> =
        if (groupByField != null) {
            sorted.groupBy { item -> valueFor(item, groupByField).ifBlank { "(Empty)" } }
        } else {
            mapOf("_all" to sorted)
        }

    return TransformedItems(processed = sorted, grouped = grouped)
}

// Produce the display text used in cells for measurement purposes (single-line content baseline)
private fun getDisplayTextForMeasure(
    field: Field,
    raw: String?,
): String {
    val value = raw ?: ""
    return when (field.getType()) {
        com.collabtable.app.data.model.FieldType.TEXT,
        com.collabtable.app.data.model.FieldType.MULTILINE_TEXT,
        com.collabtable.app.data.model.FieldType.NUMBER,
        com.collabtable.app.data.model.FieldType.DROPDOWN,
        com.collabtable.app.data.model.FieldType.AUTOCOMPLETE,
        com.collabtable.app.data.model.FieldType.DURATION,
        com.collabtable.app.data.model.FieldType.LOCATION,
        com.collabtable.app.data.model.FieldType.DATE,
        com.collabtable.app.data.model.FieldType.TIME,
        com.collabtable.app.data.model.FieldType.DATETIME,
        -> value

        com.collabtable.app.data.model.FieldType.CURRENCY -> if (value.isBlank()) "" else field.getCurrency() + value
        com.collabtable.app.data.model.FieldType.PERCENTAGE -> if (value.isBlank()) "" else "$value%"
        com.collabtable.app.data.model.FieldType.CHECKBOX -> if (value == "true") "✓" else ""
        com.collabtable.app.data.model.FieldType.SWITCH -> if (value == "true") "ON" else "OFF"
        com.collabtable.app.data.model.FieldType.URL -> value
        com.collabtable.app.data.model.FieldType.EMAIL -> value
        com.collabtable.app.data.model.FieldType.PHONE -> value
        com.collabtable.app.data.model.FieldType.RATING -> {
            val n = value.toIntOrNull() ?: 0
            "★".repeat(n)
        }
        com.collabtable.app.data.model.FieldType.COLOR -> value
        com.collabtable.app.data.model.FieldType.IMAGE -> if (value.isBlank()) "" else "🖼️ $value"
        com.collabtable.app.data.model.FieldType.FILE -> if (value.isBlank()) "" else "📎 $value"
        com.collabtable.app.data.model.FieldType.BARCODE -> value
        com.collabtable.app.data.model.FieldType.SIGNATURE -> if (value.isBlank()) "" else "✍️ Signed"
    }
}

// Compact signature to detect when a field's content or definition changed without scanning all values deeply
private data class AutoFitSignature(
    val name: String,
    val fieldUpdatedAt: Long,
    val itemCount: Int,
    val maxValueUpdatedAt: Long,
)

// Centralized mapping from canonical/legacy field type strings to user-facing labels
private fun fieldTypeToLabel(type: String): String =
    when (type.uppercase()) {
        "TEXT", "STRING" -> "Text"
        "MULTILINE_TEXT" -> "Multi-line Text"
        "NUMBER" -> "Number"
        "CURRENCY", "PRICE" -> "Currency"
        "PERCENTAGE" -> "Percentage"
        "DROPDOWN" -> "Dropdown"
        "AUTOCOMPLETE" -> "Autocomplete"
        "CHECKBOX" -> "Checkbox"
        "SWITCH" -> "Switch"
        "URL" -> "URL"
        "EMAIL" -> "Email"
        "PHONE" -> "Phone"
        "DATE" -> "Date"
        "TIME" -> "Time"
        "DATETIME" -> "Date & Time"
        "DURATION" -> "Duration"
        "IMAGE" -> "Image"
        "FILE" -> "File"
        "BARCODE" -> "Barcode"
        "SIGNATURE" -> "Signature"
        "RATING" -> "Rating"
        "COLOR" -> "Color"
        "LOCATION" -> "Location"
        else -> "Text"
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FieldHeader(
    field: Field,
    width: Dp,
    onWidthChange: (Float) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    isLast: Boolean,
    onHeaderClick: () -> Unit,
    alignment: String = "start",
    onAlignmentChange: (String) -> Unit = {},
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier =
            Modifier
                .width(width)
                .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable { onHeaderClick() },
                    textAlign =
                        when (alignment) {
                            "center" -> TextAlign.Center
                            "end" -> TextAlign.End
                            else -> TextAlign.Start
                        },
                )

                // Resize handle
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        // no-op
                                    },
                                    onDragEnd = {
                                        autoScrollJob?.cancel()
                                        autoScrollJob = null
                                    },
                                    onDragCancel = {
                                        autoScrollJob?.cancel()
                                        autoScrollJob = null
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    with(density) {
                                        onWidthChange(dragAmount.x.toDp().value)
                                    }
                                    val leftCancelThreshold = -4f
                                    val autoStepDp = 2f
                                    if (isLast) {
                                        // Start or maintain continuous expand+scroll when at/right of the handle
                                        if (dragAmount.x >= 0f || autoScrollJob != null) {
                                            if (autoScrollJob == null) {
                                                autoScrollJob =
                                                    scope.launch {
                                                        while (isActive) {
                                                            // Keep expanding a bit and reveal the end as space grows
                                                            onWidthChange(autoStepDp)
                                                            scrollState.scrollTo(scrollState.maxValue)
                                                            delay(16)
                                                        }
                                                    }
                                            }
                                        }
                                        // Cancel only on a clear leftward move to avoid jitter stopping expansion
                                        if (dragAmount.x < leftCancelThreshold) {
                                            autoScrollJob?.cancel()
                                            autoScrollJob = null
                                        }
                                    } else {
                                        // Non-last columns: gentle nudge near edges
                                        val edgePx = with(density) { 32.dp.toPx() }
                                        if (dragAmount.x > 0f && scrollState.value.toFloat() >= (scrollState.maxValue - edgePx)) {
                                            val target = (scrollState.value.toFloat() + edgePx / 2f).coerceAtMost(scrollState.maxValue.toFloat())
                                            scope.launch { scrollState.scrollTo(target.toInt()) }
                                        } else if (dragAmount.x < 0f && scrollState.value.toFloat() <= edgePx) {
                                            val target = (scrollState.value.toFloat() - edgePx / 2f).coerceAtLeast(0f)
                                            scope.launch { scrollState.scrollTo(target.toInt()) }
                                        }
                                    }
                                }
                            },
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Resize",
                        modifier =
                            Modifier
                                .size(16.dp)
                                .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemRow(
    fields: List<Field>,
    fieldWidths: Map<String, Dp>,
    fieldAlignments: Map<String, String>,
    itemWithValues: ItemWithValues,
    onClick: () -> Unit,
) {
    // Map values by fieldId to ensure correct value-column alignment regardless of original order
    val valuesByFieldId =
        remember(itemWithValues.values) {
            itemWithValues.values.associateBy { it.fieldId }
        }
    // Build widths list directly from the SnapshotStateMap so per-column changes recompose rows immediately
    val widths = fields.map { fieldWidths[it.id] ?: 150.dp }

    EqualHeightRow(
        widths = widths,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick),
    ) {
        fields.forEach { field ->
            key(field.id) {
                val value = valuesByFieldId[field.id]
                val alignment =
                    when (fieldAlignments[field.id]?.lowercase()) {
                        "center" -> Alignment.Center
                        "end", "right" -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }
                val textAlign =
                    when (fieldAlignments[field.id]?.lowercase()) {
                        "center" -> TextAlign.Center
                        "end", "right" -> TextAlign.End
                        else -> TextAlign.Start
                    }

                Box(
                    modifier =
                        Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                            ).padding(8.dp),
                ) {
                    when (field.getType()) {
                        com.collabtable.app.data.model.FieldType.TEXT -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.MULTILINE_TEXT -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.NUMBER -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.CURRENCY -> {
                            Text(
                                text =
                                    if (value?.value.isNullOrBlank()) {
                                        ""
                                    } else {
                                        "${field.getCurrency()}${value?.value}"
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.PERCENTAGE -> {
                            Text(
                                text = if (value?.value.isNullOrBlank()) "" else "${value?.value}%",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.DROPDOWN -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.CHECKBOX -> {
                            Text(
                                text = if (value?.value == "true") "✓" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.URL -> {
                            val uriHandler = LocalUriHandler.current
                            val urlValue = value?.value ?: ""
                            if (urlValue.isNotBlank()) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
                                    ClickableText(
                                        text =
                                            buildAnnotatedString {
                                                withStyle(
                                                    style =
                                                        SpanStyle(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            textDecoration = TextDecoration.Underline,
                                                        ),
                                                ) {
                                                    append(urlValue)
                                                }
                                            },
                                        onClick = {
                                            try {
                                                uriHandler.openUri(urlValue)
                                            } catch (e: Exception) {
                                                // Handle invalid URL
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            }
                        }

                        com.collabtable.app.data.model.FieldType.EMAIL -> {
                            val uriHandler = LocalUriHandler.current
                            val emailValue = value?.value ?: ""
                            if (emailValue.isNotBlank()) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
                                    ClickableText(
                                        text =
                                            buildAnnotatedString {
                                                withStyle(
                                                    style =
                                                        SpanStyle(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            textDecoration = TextDecoration.Underline,
                                                        ),
                                                ) {
                                                    append(emailValue)
                                                }
                                            },
                                        onClick = {
                                            try {
                                                uriHandler.openUri("mailto:$emailValue")
                                            } catch (e: Exception) {
                                                // Handle invalid email
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            }
                        }

                        com.collabtable.app.data.model.FieldType.PHONE -> {
                            val uriHandler = LocalUriHandler.current
                            val phoneValue = value?.value ?: ""
                            if (phoneValue.isNotBlank()) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
                                    ClickableText(
                                        text =
                                            buildAnnotatedString {
                                                withStyle(
                                                    style =
                                                        SpanStyle(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            textDecoration = TextDecoration.Underline,
                                                        ),
                                                ) {
                                                    append(phoneValue)
                                                }
                                            },
                                        onClick = {
                                            try {
                                                uriHandler.openUri("tel:$phoneValue")
                                            } catch (e: Exception) {
                                                // Handle invalid phone
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            }
                        }

                        com.collabtable.app.data.model.FieldType.DATE -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.TIME -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.DATETIME -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        // New field types
                        com.collabtable.app.data.model.FieldType.SWITCH -> {
                            Text(
                                text = if (value?.value == "true") "ON" else "OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (value?.value == "true") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.AUTOCOMPLETE -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.DURATION -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.RATING -> {
                            val rating = value?.value?.toIntOrNull() ?: 0
                            val maxRating = field.getMaxRating()
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
                                Row(
                                    modifier =
                                        Modifier
                                            .padding(vertical = 8.dp),
                                ) {
                                    repeat(maxRating) { index ->
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint =
                                                if (index < rating) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        com.collabtable.app.data.model.FieldType.COLOR -> {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (value?.value?.isNotBlank() == true) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .background(
                                                    color =
                                                        try {
                                                            androidx.compose.ui.graphics.Color(
                                                                android.graphics.Color.parseColor(value.value),
                                                            )
                                                        } catch (e: Exception) {
                                                            MaterialTheme.colorScheme.surface
                                                        },
                                                    shape = RoundedCornerShape(4.dp),
                                                ).border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(4.dp),
                                                ),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
                                    Text(
                                        text = value?.value ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        com.collabtable.app.data.model.FieldType.LOCATION -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.IMAGE -> {
                            if (value?.value?.isNotBlank() == true) {
                                Text(
                                    text = "🖼️ ${value.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            }
                        }

                        com.collabtable.app.data.model.FieldType.FILE -> {
                            if (value?.value?.isNotBlank() == true) {
                                Text(
                                    text = "📎 ${value.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            }
                        }

                        com.collabtable.app.data.model.FieldType.BARCODE -> {
                            Text(
                                text = value?.value ?: "",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    ),
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            )
                        }

                        com.collabtable.app.data.model.FieldType.SIGNATURE -> {
                            if (value?.value?.isNotBlank() == true) {
                                Text(
                                    text = "✍️ Signed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = textAlign,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualHeightRow(
    widths: List<Dp>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val widthsPx = remember(widths, density) { widths.map { with(density) { it.roundToPx() } } }
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, outerConstraints ->
        val count = measurables.size
        val n = widthsPx.size.coerceAtMost(count)

        // Compute required row height using intrinsics to avoid measuring the same child twice
        var rowHeight = outerConstraints.minHeight
        for (i in 0 until n) {
            val w = widthsPx[i].coerceAtLeast(0)
            val h = measurables[i].maxIntrinsicHeight(w)
            if (h > rowHeight) rowHeight = h
        }
        rowHeight = rowHeight.coerceIn(outerConstraints.minHeight, outerConstraints.maxHeight)

        // Measure once with fixed width and the computed row height so all cells match
        val placeables =
            Array(n) { i ->
                val w = widthsPx[i].coerceAtLeast(0)
                val c = Constraints.fixed(width = w, height = rowHeight)
                measurables[i].measure(c)
            }

        val totalWidth = widthsPx.take(n).sum()
        val layoutWidth = totalWidth.coerceIn(outerConstraints.minWidth, outerConstraints.maxWidth)
        val layoutHeight = rowHeight

        layout(layoutWidth, layoutHeight) {
            var x = 0
            for (i in 0 until n) {
                val p = placeables[i]
                p.placeRelative(x, 0)
                x += p.width
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit,
) {
    var fieldName by remember { mutableStateOf("") }
    var selectedFieldType by remember { mutableStateOf("TEXT") }
    var dropdownOptions by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("$") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_field)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Field Type Selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = fieldTypeToLabel(selectedFieldType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Field Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        // Text types
                        DropdownMenuItem(
                            text = { Text("Text") },
                            onClick = {
                                selectedFieldType = "TEXT"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Multi-line Text") },
                            onClick = {
                                selectedFieldType = "MULTILINE_TEXT"
                                expanded = false
                            },
                        )

                        // Number types
                        DropdownMenuItem(
                            text = { Text("Number") },
                            onClick = {
                                selectedFieldType = "NUMBER"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Currency") },
                            onClick = {
                                selectedFieldType = "CURRENCY"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Percentage") },
                            onClick = {
                                selectedFieldType = "PERCENTAGE"
                                expanded = false
                            },
                        )

                        // Selection types
                        DropdownMenuItem(
                            text = { Text("Dropdown") },
                            onClick = {
                                selectedFieldType = "DROPDOWN"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Autocomplete") },
                            onClick = {
                                selectedFieldType = "AUTOCOMPLETE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Checkbox") },
                            onClick = {
                                selectedFieldType = "CHECKBOX"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Switch") },
                            onClick = {
                                selectedFieldType = "SWITCH"
                                expanded = false
                            },
                        )

                        // Link types
                        DropdownMenuItem(
                            text = { Text("URL") },
                            onClick = {
                                selectedFieldType = "URL"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Email") },
                            onClick = {
                                selectedFieldType = "EMAIL"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Phone") },
                            onClick = {
                                selectedFieldType = "PHONE"
                                expanded = false
                            },
                        )

                        // Date/Time types
                        DropdownMenuItem(
                            text = { Text("Date") },
                            onClick = {
                                selectedFieldType = "DATE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Time") },
                            onClick = {
                                selectedFieldType = "TIME"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Date & Time") },
                            onClick = {
                                selectedFieldType = "DATETIME"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duration") },
                            onClick = {
                                selectedFieldType = "DURATION"
                                expanded = false
                            },
                        )

                        // Media types
                        DropdownMenuItem(
                            text = { Text("Image") },
                            onClick = {
                                selectedFieldType = "IMAGE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("File") },
                            onClick = {
                                selectedFieldType = "FILE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Barcode") },
                            onClick = {
                                selectedFieldType = "BARCODE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Signature") },
                            onClick = {
                                selectedFieldType = "SIGNATURE"
                                expanded = false
                            },
                        )

                        // Other types
                        DropdownMenuItem(
                            text = { Text("Rating") },
                            onClick = {
                                selectedFieldType = "RATING"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Color") },
                            onClick = {
                                selectedFieldType = "COLOR"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Location") },
                            onClick = {
                                selectedFieldType = "LOCATION"
                                expanded = false
                            },
                        )
                    }
                }

                // Currency-specific options
                if (selectedFieldType == "CURRENCY") {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Currency Symbol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("$, €, £, etc.") },
                    )
                }

                // Dropdown-specific options
                if (selectedFieldType == "DROPDOWN") {
                    OutlinedTextField(
                        value = dropdownOptions,
                        onValueChange = { dropdownOptions = it },
                        label = { Text("Options (comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Option 1, Option 2, Option 3") },
                        supportingText = { Text("Enter dropdown choices separated by commas") },
                    )
                }

                // Autocomplete-specific options
                if (selectedFieldType == "AUTOCOMPLETE") {
                    OutlinedTextField(
                        value = dropdownOptions,
                        onValueChange = { dropdownOptions = it },
                        label = { Text("Options (comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Option 1, Option 2, Option 3") },
                        supportingText = { Text("Enter autocomplete suggestions separated by commas") },
                    )
                }

                // Rating-specific options
                if (selectedFieldType == "RATING") {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Max Rating") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("5") },
                        supportingText = { Text("Maximum number of stars (default: 5)") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fieldName.isNotBlank()) {
                        val normalizedType = selectedFieldType.uppercase()
                        val options =
                            when (normalizedType) {
                                "CURRENCY" -> currency.trim()
                                "DROPDOWN" ->
                                    dropdownOptions
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .joinToString("|")
                                "AUTOCOMPLETE" ->
                                    dropdownOptions
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .joinToString("|")
                                "RATING" -> currency.trim().ifBlank { "5" }
                                else -> ""
                            }
                        onAdd(fieldName.trim(), normalizedType, options)
                    }
                },
                enabled =
                    fieldName.isNotBlank() &&
                        (selectedFieldType.uppercase() !in listOf("DROPDOWN", "AUTOCOMPLETE") || dropdownOptions.isNotBlank()),
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFieldDialog(
    field: Field,
    onDismiss: () -> Unit,
    onUpdate: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(field.name) }
    var selectedFieldType by remember { mutableStateOf(field.fieldType.uppercase()) }
    var dropdownOptions by remember { mutableStateOf(field.getDropdownOptions().joinToString(", ")) }
    var currency by remember { mutableStateOf(field.getCurrency()) }
    var expanded by remember { mutableStateOf(false) }
    // Alignment state persisted per list/field in PreferencesManager
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    var selectedAlignment by remember {
        mutableStateOf(
            prefs.getColumnAlignments(field.listId)[field.id]?.lowercase() ?: "start",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Column") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Column Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Field Type Selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = fieldTypeToLabel(selectedFieldType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Field Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        // Text types
                        DropdownMenuItem(
                            text = { Text("Text") },
                            onClick = {
                                selectedFieldType = "TEXT"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Multi-line Text") },
                            onClick = {
                                selectedFieldType = "MULTILINE_TEXT"
                                expanded = false
                            },
                        )

                        // Number types
                        DropdownMenuItem(
                            text = { Text("Number") },
                            onClick = {
                                selectedFieldType = "NUMBER"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Currency") },
                            onClick = {
                                selectedFieldType = "CURRENCY"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Percentage") },
                            onClick = {
                                selectedFieldType = "PERCENTAGE"
                                expanded = false
                            },
                        )

                        // Selection types
                        DropdownMenuItem(
                            text = { Text("Dropdown") },
                            onClick = {
                                selectedFieldType = "DROPDOWN"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Autocomplete") },
                            onClick = {
                                selectedFieldType = "AUTOCOMPLETE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Checkbox") },
                            onClick = {
                                selectedFieldType = "CHECKBOX"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Switch") },
                            onClick = {
                                selectedFieldType = "SWITCH"
                                expanded = false
                            },
                        )

                        // Link types
                        DropdownMenuItem(
                            text = { Text("URL") },
                            onClick = {
                                selectedFieldType = "URL"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Email") },
                            onClick = {
                                selectedFieldType = "EMAIL"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Phone") },
                            onClick = {
                                selectedFieldType = "PHONE"
                                expanded = false
                            },
                        )

                        // Date/Time types
                        DropdownMenuItem(
                            text = { Text("Date") },
                            onClick = {
                                selectedFieldType = "DATE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Time") },
                            onClick = {
                                selectedFieldType = "TIME"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Date & Time") },
                            onClick = {
                                selectedFieldType = "DATETIME"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duration") },
                            onClick = {
                                selectedFieldType = "DURATION"
                                expanded = false
                            },
                        )

                        // Media types
                        DropdownMenuItem(
                            text = { Text("Image") },
                            onClick = {
                                selectedFieldType = "IMAGE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("File") },
                            onClick = {
                                selectedFieldType = "FILE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Barcode") },
                            onClick = {
                                selectedFieldType = "BARCODE"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Signature") },
                            onClick = {
                                selectedFieldType = "SIGNATURE"
                                expanded = false
                            },
                        )

                        // Other types
                        DropdownMenuItem(
                            text = { Text("Rating") },
                            onClick = {
                                selectedFieldType = "RATING"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Color") },
                            onClick = {
                                selectedFieldType = "COLOR"
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Location") },
                            onClick = {
                                selectedFieldType = "LOCATION"
                                expanded = false
                            },
                        )
                    }
                }

                // Content alignment (single-select) using a connected Material3 SegmentedButton group
                Text(
                    text = "Content Alignment",
                    style = MaterialTheme.typography.titleSmall,
                )
                val alignmentOptions = listOf("start" to "Left", "center" to "Center", "end" to "Right")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 4.dp)) {
                    alignmentOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = selectedAlignment == value,
                            onClick = { selectedAlignment = value },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = alignmentOptions.size),
                            icon = { if (selectedAlignment == value) Icon(Icons.Default.Check, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }

                // Currency-specific options
                if (selectedFieldType == "CURRENCY" || selectedFieldType == "PRICE") {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Currency Symbol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("$, €, £, etc.") },
                    )
                }

                // Dropdown-specific options
                if (selectedFieldType == "DROPDOWN") {
                    OutlinedTextField(
                        value = dropdownOptions,
                        onValueChange = { dropdownOptions = it },
                        label = { Text("Options (comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Option 1, Option 2, Option 3") },
                        supportingText = { Text("Enter dropdown choices separated by commas") },
                    )
                }

                // Autocomplete-specific options
                if (selectedFieldType == "AUTOCOMPLETE") {
                    OutlinedTextField(
                        value = dropdownOptions,
                        onValueChange = { dropdownOptions = it },
                        label = { Text("Options (comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Option 1, Option 2, Option 3") },
                        supportingText = { Text("Enter autocomplete suggestions separated by commas") },
                    )
                }

                // Rating-specific options
                if (selectedFieldType == "RATING") {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Max Rating") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("5") },
                        supportingText = { Text("Maximum number of stars (default: 5)") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedType = selectedFieldType.uppercase()
                    val options =
                        when (normalizedType) {
                            "CURRENCY", "PRICE" -> currency.trim()
                            "DROPDOWN" ->
                                dropdownOptions
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("|")
                            "AUTOCOMPLETE" ->
                                dropdownOptions
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("|")
                            "RATING" -> currency.trim().ifBlank { "5" }
                            else -> ""
                        }
                    onUpdate(name.trim(), normalizedType, options)
                    // Persist alignment selection for this field
                    val current = prefs.getColumnAlignments(field.listId).toMutableMap()
                    current[field.id] =
                        when (selectedAlignment.lowercase()) {
                            "center" -> "center"
                            "end", "right" -> "end"
                            else -> "start"
                        }
                    prefs.setColumnAlignments(field.listId, current)
                },
                enabled = name.isNotBlank() && (selectedFieldType !in listOf("DROPDOWN", "AUTOCOMPLETE") || dropdownOptions.isNotBlank()),
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    fields: List<Field>,
    onDismiss: () -> Unit,
    onAdd: (Map<String, String>) -> Unit,
) {
    val fieldValues = remember { mutableStateMapOf<String, String>() }

    // Initialize all field values to empty strings
    LaunchedEffect(fields) {
        fields.forEach { field ->
            if (!fieldValues.containsKey(field.id)) {
                fieldValues[field.id] = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_item)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(fields, key = { it.id }) { field ->
                    FieldInput(
                        field = field,
                        value = fieldValues[field.id].orEmpty(),
                        onValueChange = { newValue ->
                            fieldValues[field.id] = newValue
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(fieldValues.toMap()) },
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldInput(
    field: Field,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (field.getType()) {
        // Text types
        com.collabtable.app.data.model.FieldType.TEXT -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        com.collabtable.app.data.model.FieldType.MULTILINE_TEXT -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )
        }

        // Number types
        com.collabtable.app.data.model.FieldType.NUMBER -> {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' || it == '-' }
                    onValueChange(filtered)
                },
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("0") },
            )
        }

        com.collabtable.app.data.model.FieldType.CURRENCY -> {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    onValueChange(filtered)
                },
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Text(field.getCurrency()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0.00") },
            )
        }

        com.collabtable.app.data.model.FieldType.PERCENTAGE -> {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    onValueChange(filtered)
                },
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0") },
            )
        }

        // Selection types
        com.collabtable.app.data.model.FieldType.CHECKBOX -> {
            var checked by remember { mutableStateOf(value == "true") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        onValueChange(it.toString())
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        com.collabtable.app.data.model.FieldType.DROPDOWN -> {
            val options = field.getDropdownOptions()
            var dropdownExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(field.name) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    modifier =
                        Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                dropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }

        com.collabtable.app.data.model.FieldType.URL -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                placeholder = { Text("https://example.com") },
            )
        }

        com.collabtable.app.data.model.FieldType.EMAIL -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                placeholder = { Text("email@example.com") },
            )
        }

        com.collabtable.app.data.model.FieldType.PHONE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                placeholder = { Text("+1 (555) 123-4567") },
            )
        }

        // Date/Time types
        com.collabtable.app.data.model.FieldType.DATE -> {
            val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            val calendar = remember { Calendar.getInstance() }
            var showDatePicker by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.name) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                singleLine = true,
                placeholder = { Text("Select date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick date")
                    }
                },
            )

            if (showDatePicker) {
                val datePickerState =
                    rememberDatePickerState(
                        initialSelectedDateMillis =
                            if (value.isBlank()) {
                                System.currentTimeMillis()
                            } else {
                                try {
                                    dateFormat.parse(value)?.time
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                            },
                    )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                calendar.timeInMillis = millis
                                val formattedDate = dateFormat.format(calendar.time)
                                onValueChange(formattedDate)
                            }
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }

        com.collabtable.app.data.model.FieldType.TIME -> {
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val calendar = remember { Calendar.getInstance() }
            var showTimePicker by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.name) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                singleLine = true,
                placeholder = { Text("Select time") },
                trailingIcon = {
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Default.AccountBox, contentDescription = "Pick time")
                    }
                },
            )

            if (showTimePicker) {
                val timePickerState =
                    rememberTimePickerState(
                        initialHour =
                            if (value.isBlank()) {
                                Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            } else {
                                try {
                                    timeFormat.parse(value)?.let {
                                        calendar.time = it
                                        calendar.get(Calendar.HOUR_OF_DAY)
                                    } ?: 12
                                } catch (e: Exception) {
                                    12
                                }
                            },
                        initialMinute =
                            if (value.isBlank()) {
                                Calendar.getInstance().get(Calendar.MINUTE)
                            } else {
                                try {
                                    timeFormat.parse(value)?.let {
                                        calendar.time = it
                                        calendar.get(Calendar.MINUTE)
                                    } ?: 0
                                } catch (e: Exception) {
                                    0
                                }
                            },
                    )

                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            calendar.set(Calendar.MINUTE, timePickerState.minute)
                            val formattedTime = timeFormat.format(calendar.time)
                            onValueChange(formattedTime)
                            showTimePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                    },
                    text = {
                        TimePicker(state = timePickerState)
                    },
                )
            }
        }

        com.collabtable.app.data.model.FieldType.DATETIME -> {
            val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val calendar = remember { Calendar.getInstance() }
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }
            var tempDate by remember { mutableStateOf("") }

            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.name) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                singleLine = true,
                placeholder = { Text("Select date & time") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick date & time")
                    }
                },
            )

            if (showDatePicker) {
                val datePickerState =
                    rememberDatePickerState(
                        initialSelectedDateMillis = System.currentTimeMillis(),
                    )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                calendar.timeInMillis = millis
                                tempDate = dateFormat.format(calendar.time)
                            }
                            showDatePicker = false
                            showTimePicker = true
                        }) {
                            Text("Next")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showTimePicker) {
                val timePickerState =
                    rememberTimePickerState(
                        initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                        initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
                    )

                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            calendar.set(Calendar.MINUTE, timePickerState.minute)
                            val time = timeFormat.format(calendar.time)
                            val dateTime = "$tempDate $time"
                            onValueChange(dateTime)
                            showTimePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                    },
                    text = {
                        TimePicker(state = timePickerState)
                    },
                )
            }
        }

        // New field types
        com.collabtable.app.data.model.FieldType.SWITCH -> {
            var checked by remember { mutableStateOf(value == "true") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        onValueChange(it.toString())
                    },
                )
            }
        }

        com.collabtable.app.data.model.FieldType.AUTOCOMPLETE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Start typing...") },
                supportingText = {
                    val options = field.getAutocompleteOptions()
                    if (options.isNotEmpty()) {
                        Text("Suggestions: ${options.take(3).joinToString(", ")}")
                    }
                },
            )
        }

        com.collabtable.app.data.model.FieldType.DURATION -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("2:30 (hours:minutes)") },
                supportingText = { Text("Format: HH:MM") },
            )
        }

        com.collabtable.app.data.model.FieldType.RATING -> {
            val maxRating = field.getMaxRating()
            var rating by remember { mutableStateOf(value.toIntOrNull() ?: 0) }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    for (i in 1..maxRating) {
                        IconButton(
                            onClick = {
                                rating = i
                                onValueChange(i.toString())
                            },
                        ) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.Star,
                                contentDescription = "Star $i",
                                tint =
                                    if (i <= rating) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                            )
                        }
                    }
                }
            }
        }

        com.collabtable.app.data.model.FieldType.COLOR -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("#FF5733 or red") },
                leadingIcon = {
                    // Show color preview if valid
                    if (value.isNotBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .background(
                                        color =
                                            try {
                                                androidx.compose.ui.graphics
                                                    .Color(android.graphics.Color.parseColor(value))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.surface
                                            },
                                        shape = RoundedCornerShape(4.dp),
                                    ).border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(4.dp),
                                    ),
                        )
                    }
                },
            )
        }

        com.collabtable.app.data.model.FieldType.LOCATION -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Address or coordinates") },
            )
        }

        com.collabtable.app.data.model.FieldType.IMAGE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Image URL") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }

        com.collabtable.app.data.model.FieldType.FILE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("File path or URL") },
            )
        }

        com.collabtable.app.data.model.FieldType.BARCODE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Scan or enter barcode") },
            )
        }

        com.collabtable.app.data.model.FieldType.SIGNATURE -> {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(field.name) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Tap to sign") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageColumnsDialog(
    fields: List<Field>,
    onDismiss: () -> Unit,
    onAddField: (String, String, String) -> Unit,
    onUpdateField: (String, String, String, String) -> Unit,
    onDeleteField: (String) -> Unit,
    onReorderFields: (List<Field>) -> Unit,
) {
    var showAddField by remember { mutableStateOf(false) }
    var fieldToEdit by remember { mutableStateOf<Field?>(null) }
    var fieldToDelete by remember { mutableStateOf<Field?>(null) }

    // Keep a single stable list instance; synchronize its contents from 'fields'
    val reorderedFields = remember { mutableStateListOf<Field>() }

    // Watch for changes in fields and update reorderedFields
    LaunchedEffect(fields) {
        val currentIds = reorderedFields.map { it.id }.toSet()
        val newIds = fields.map { it.id }.toSet()

        // Add new fields
        fields.filter { it.id !in currentIds }.forEach { newField ->
            reorderedFields.add(newField)
        }

        // Remove deleted fields
        reorderedFields.removeAll { it.id !in newIds }

        // Update existing fields (in case of edits)
        fields.forEach { updatedField ->
            val index = reorderedFields.indexOfFirst { it.id == updatedField.id }
            if (index >= 0 && reorderedFields[index] != updatedField) {
                reorderedFields[index] = updatedField
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Manage Columns",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Column list with drag-and-drop reordering
                // Local extension mirroring the working behavior used in ListsScreen,
                // adjusting target index when moving downwards to account for prior removal.
                fun <T> MutableList<T>.move(
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
                            // Indices from reorderable refer to draggable items only
                            reorderedFields.move(from.index, to.index)
                        },
                    )

                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .reorderable(reorderState),
                    state = reorderState.listState,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(reorderedFields, key = { _, field -> field.id }) { index, field ->
                        ReorderableItem(
                            reorderState,
                            key = field.id,
                        ) { _ ->
                            Box(
                                modifier = Modifier.animateItemPlacement(),
                            ) {
                                ColumnItem(
                                    field = field,
                                    onEdit = { fieldToEdit = field },
                                    onDelete = { fieldToDelete = field },
                                    onMoveUp = {
                                        if (index > 0) {
                                            val item = reorderedFields.removeAt(index)
                                            reorderedFields.add(index - 1, item)
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < reorderedFields.size - 1) {
                                            val item = reorderedFields.removeAt(index)
                                            reorderedFields.add(index + 1, item)
                                        }
                                    },
                                    canMoveUp = index > 0,
                                    canMoveDown = index < reorderedFields.size - 1,
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

                HorizontalDivider()

                // Add button
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(
                        onClick = { showAddField = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Column")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onReorderFields(reorderedFields.toList())
                            onDismiss()
                        },
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    if (showAddField) {
        AddFieldDialog(
            onDismiss = { showAddField = false },
            onAdd = { name, fieldType, fieldOptions ->
                onAddField(name, fieldType, fieldOptions)
                showAddField = false
            },
        )
    }

    fieldToEdit?.let { field ->
        EditFieldDialog(
            field = field,
            onDismiss = { fieldToEdit = null },
            onUpdate = { name, fieldType, fieldOptions ->
                onUpdateField(field.id, name, fieldType, fieldOptions)
                fieldToEdit = null
            },
        )
    }

    fieldToDelete?.let { field ->
        AlertDialog(
            onDismissRequest = { fieldToDelete = null },
            title = { Text("Delete Column") },
            text = { Text("Are you sure you want to delete '${field.name}'? All data in this column will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteField(field.id)
                        fieldToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fieldToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun ColumnItem(
    field: Field,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle
        if (dragHandle != null) {
            dragHandle()
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = field.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    when (field.getType()) {
                        // Text types
                        com.collabtable.app.data.model.FieldType.TEXT -> "Text"
                        com.collabtable.app.data.model.FieldType.MULTILINE_TEXT -> "Multiline Text"

                        // Number types
                        com.collabtable.app.data.model.FieldType.NUMBER -> "Number"
                        com.collabtable.app.data.model.FieldType.CURRENCY -> "Currency (${field.getCurrency()})"
                        com.collabtable.app.data.model.FieldType.PERCENTAGE -> "Percentage"

                        // Selection types
                        com.collabtable.app.data.model.FieldType.CHECKBOX -> "Checkbox"
                        com.collabtable.app.data.model.FieldType.SWITCH -> "Switch"
                        com.collabtable.app.data.model.FieldType.DROPDOWN ->
                            "Dropdown (${field.getDropdownOptions().size} options)"
                        com.collabtable.app.data.model.FieldType.AUTOCOMPLETE ->
                            "Autocomplete (${field.getAutocompleteOptions().size} options)"

                        // Link types
                        com.collabtable.app.data.model.FieldType.URL -> "URL"
                        com.collabtable.app.data.model.FieldType.EMAIL -> "Email"
                        com.collabtable.app.data.model.FieldType.PHONE -> "Phone"

                        // Date/Time types
                        com.collabtable.app.data.model.FieldType.DATE -> "Date"
                        com.collabtable.app.data.model.FieldType.TIME -> "Time"
                        com.collabtable.app.data.model.FieldType.DATETIME -> "Date & Time"
                        com.collabtable.app.data.model.FieldType.DURATION -> "Duration"

                        // Media types
                        com.collabtable.app.data.model.FieldType.IMAGE -> "Image"
                        com.collabtable.app.data.model.FieldType.FILE -> "File"
                        com.collabtable.app.data.model.FieldType.BARCODE -> "Barcode"
                        com.collabtable.app.data.model.FieldType.SIGNATURE -> "Signature"

                        // Other types
                        com.collabtable.app.data.model.FieldType.RATING -> "Rating (${field.getMaxRating()} stars)"
                        com.collabtable.app.data.model.FieldType.COLOR -> "Color"
                        com.collabtable.app.data.model.FieldType.LOCATION -> "Location"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Edit button
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Delete button
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemDialog(
    fields: List<Field>,
    itemWithValues: ItemWithValues,
    onDismiss: () -> Unit,
    onUpdate: (Map<String, String>) -> Unit,
    onDelete: () -> Unit,
) {
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    // Map values by field to avoid linear lookups and ensure consistent mapping when fields reorder
    val valuesByFieldId =
        remember(itemWithValues.values) {
            itemWithValues.values.associateBy { it.fieldId }
        }

    // Initialize field values from existing item
    LaunchedEffect(itemWithValues) {
        itemWithValues.values.forEach { itemValue ->
            fieldValues[itemValue.id] = itemValue.value
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Edit Item",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Fields list
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(fields, key = { it.id }) { field ->
                        val itemValue = valuesByFieldId[field.id]
                        if (itemValue != null) {
                            FieldInput(
                                field = field,
                                value = fieldValues[itemValue.id].orEmpty(),
                                onValueChange = { newValue ->
                                    fieldValues[itemValue.id] = newValue
                                },
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                onUpdate(fieldValues.toMap())
                            },
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortDialog(
    fields: List<Field>,
    currentSortField: Field?,
    currentSortAscending: Boolean,
    currentGroupByField: Field?,
    currentFilterField: Field?,
    currentFilterValue: String,
    onDismiss: () -> Unit,
    onApply: (sortField: Field?, sortAscending: Boolean, groupByField: Field?, filterField: Field?, filterValue: String) -> Unit,
    onClearAll: () -> Unit,
) {
    var sortField by remember { mutableStateOf(currentSortField) }
    var sortAscending by remember { mutableStateOf(currentSortAscending) }
    var groupByField by remember { mutableStateOf(currentGroupByField) }
    var filterField by remember { mutableStateOf(currentFilterField) }
    var filterValue by remember { mutableStateOf(currentFilterValue) }

    var sortFieldExpanded by remember { mutableStateOf(false) }
    var groupByFieldExpanded by remember { mutableStateOf(false) }
    var filterFieldExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filter, Sort & Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Sort Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sort By",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        ExposedDropdownMenuBox(
                            expanded = sortFieldExpanded,
                            onExpandedChange = { sortFieldExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = sortField?.name ?: "None",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sort Field") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortFieldExpanded) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                            )

                            ExposedDropdownMenu(
                                expanded = sortFieldExpanded,
                                onDismissRequest = { sortFieldExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        sortField = null
                                        sortFieldExpanded = false
                                    },
                                )
                                fields.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.name) },
                                        onClick = {
                                            sortField = field
                                            sortFieldExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        if (sortField != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Order:", modifier = Modifier.padding(end = 8.dp))
                                FilterChip(
                                    selected = sortAscending,
                                    onClick = { sortAscending = true },
                                    label = { Text("Ascending") },
                                    leadingIcon =
                                        if (sortAscending) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilterChip(
                                    selected = !sortAscending,
                                    onClick = { sortAscending = false },
                                    label = { Text("Descending") },
                                    leadingIcon =
                                        if (!sortAscending) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Group By Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Group By",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        ExposedDropdownMenuBox(
                            expanded = groupByFieldExpanded,
                            onExpandedChange = { groupByFieldExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = groupByField?.name ?: "None",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Group By Field") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupByFieldExpanded) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                            )

                            ExposedDropdownMenu(
                                expanded = groupByFieldExpanded,
                                onDismissRequest = { groupByFieldExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        groupByField = null
                                        groupByFieldExpanded = false
                                    },
                                )
                                fields.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.name) },
                                        onClick = {
                                            groupByField = field
                                            groupByFieldExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Filter Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        ExposedDropdownMenuBox(
                            expanded = filterFieldExpanded,
                            onExpandedChange = { filterFieldExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = filterField?.name ?: "None",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Filter Field") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterFieldExpanded) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                            )

                            ExposedDropdownMenu(
                                expanded = filterFieldExpanded,
                                onDismissRequest = { filterFieldExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        filterField = null
                                        filterValue = ""
                                        filterFieldExpanded = false
                                    },
                                )
                                fields.forEach { field ->
                                    DropdownMenuItem(
                                        text = { Text(field.name) },
                                        onClick = {
                                            filterField = field
                                            filterFieldExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        if (filterField != null) {
                            OutlinedTextField(
                                value = filterValue,
                                onValueChange = { filterValue = it },
                                label = { Text("Filter Value") },
                                placeholder = { Text("Enter text to filter...") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = onClearAll) {
                        Text("Clear All")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                onApply(sortField, sortAscending, groupByField, filterField, filterValue)
                            },
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDialog(
    fields: List<Field>,
    currentSortField: Field?,
    currentSortAscending: Boolean,
    onDismiss: () -> Unit,
    onApply: (sortField: Field?, sortAscending: Boolean) -> Unit,
) {
    var sortField by remember { mutableStateOf(currentSortField) }
    var sortAscending by remember { mutableStateOf(currentSortAscending) }
    var sortFieldExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sort Items",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sort Field Selection
                ExposedDropdownMenuBox(
                    expanded = sortFieldExpanded,
                    onExpandedChange = { sortFieldExpanded = it },
                ) {
                    OutlinedTextField(
                        value = sortField?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sort Field") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortFieldExpanded) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                    )

                    ExposedDropdownMenu(
                        expanded = sortFieldExpanded,
                        onDismissRequest = { sortFieldExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                sortField = null
                                sortFieldExpanded = false
                            },
                        )
                        fields.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.name) },
                                onClick = {
                                    sortField = field
                                    sortFieldExpanded = false
                                },
                            )
                        }
                    }
                }

                // Sort Order Selection
                if (sortField != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Order",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = sortAscending,
                            onClick = { sortAscending = true },
                            label = { Text("Ascending ↑") },
                            modifier = Modifier.weight(1f),
                            leadingIcon =
                                if (sortAscending) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                        )
                        FilterChip(
                            selected = !sortAscending,
                            onClick = { sortAscending = false },
                            label = { Text("Descending ↓") },
                            modifier = Modifier.weight(1f),
                            leadingIcon =
                                if (!sortAscending) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(sortField, sortAscending) },
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDialog(
    fields: List<Field>,
    currentGroupByField: Field?,
    onDismiss: () -> Unit,
    onApply: (groupByField: Field?) -> Unit,
) {
    var groupByField by remember { mutableStateOf(currentGroupByField) }
    var groupByFieldExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Group Items",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Group items by a field to organize them into categories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Group By Field Selection
                ExposedDropdownMenuBox(
                    expanded = groupByFieldExpanded,
                    onExpandedChange = { groupByFieldExpanded = it },
                ) {
                    OutlinedTextField(
                        value = groupByField?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Group By Field") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupByFieldExpanded) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                    )

                    ExposedDropdownMenu(
                        expanded = groupByFieldExpanded,
                        onDismissRequest = { groupByFieldExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                groupByField = null
                                groupByFieldExpanded = false
                            },
                        )
                        fields.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.name) },
                                onClick = {
                                    groupByField = field
                                    groupByFieldExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(groupByField) },
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    fields: List<Field>,
    currentFilterField: Field?,
    currentFilterValue: String,
    onDismiss: () -> Unit,
    onApply: (filterField: Field?, filterValue: String) -> Unit,
) {
    var filterField by remember { mutableStateOf(currentFilterField) }
    var filterValue by remember { mutableStateOf(currentFilterValue) }
    var filterFieldExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filter Items",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Show only items that contain specific text in a field",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Filter Field Selection
                ExposedDropdownMenuBox(
                    expanded = filterFieldExpanded,
                    onExpandedChange = { filterFieldExpanded = it },
                ) {
                    OutlinedTextField(
                        value = filterField?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filter Field") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterFieldExpanded) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                    )

                    ExposedDropdownMenu(
                        expanded = filterFieldExpanded,
                        onDismissRequest = { filterFieldExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                filterField = null
                                filterFieldExpanded = false
                            },
                        )
                        fields.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.name) },
                                onClick = {
                                    filterField = field
                                    filterFieldExpanded = false
                                },
                            )
                        }
                    }
                }

                // Filter Value Input
                if (filterField != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { filterValue = it },
                        label = { Text("Filter Value") },
                        placeholder = { Text("Enter text to filter...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(filterField, filterValue) },
                        enabled = filterField == null || filterValue.isNotBlank(),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameListDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename List",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a new name for this list",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("List Name") },
                    placeholder = { Text("Enter list name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName) },
                enabled = newName.isNotBlank() && newName.trim() != currentName,
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
