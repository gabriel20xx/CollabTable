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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.collabtable.app.R
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.ui.components.ConnectionStatusAction
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.Field
import com.collabtable.app.data.model.ItemWithValues
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

    val list by viewModel.list.collectAsState()
    val fields by viewModel.fields.collectAsState()
    val items by viewModel.items.collectAsState()

    // Use derivedStateOf to create stable references
    val stableFields by remember { derivedStateOf { fields } }
    val stableItems by remember { derivedStateOf { items } }

    var showManageColumnsDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ItemWithValues?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }

    // Filter/Sort state
    var sortField by remember { mutableStateOf<Field?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    var groupByField by remember { mutableStateOf<Field?>(null) }
    var filterField by remember { mutableStateOf<Field?>(null) }
    var filterValue by remember { mutableStateOf("") }

    // Shared scroll state for synchronized horizontal scrolling
    val horizontalScrollState = rememberScrollState()

    // Field widths state (resizable columns)
    val fieldWidths = remember { mutableStateMapOf<String, Dp>() }

    // Initialize field widths
    LaunchedEffect(stableFields) {
        stableFields.forEach { field ->
            if (!fieldWidths.containsKey(field.id)) {
                fieldWidths[field.id] = 150.dp
            }
        }
    }

    // Apply filtering, sorting, and grouping
    val processedItems =
        remember(stableItems, stableFields, filterField, filterValue, sortField, sortAscending, groupByField) {
            var result = stableItems

            // Apply filter
            if (filterField != null && filterValue.isNotBlank()) {
                result =
                    result.filter { itemWithValues ->
                        val value = itemWithValues.values.find { it.fieldId == filterField!!.id }?.value ?: ""
                        value.contains(filterValue, ignoreCase = true)
                    }
            }

            // Apply sort
            if (sortField != null) {
                result =
                    result.sortedWith(
                        compareBy { itemWithValues ->
                            val value = itemWithValues.values.find { it.fieldId == sortField!!.id }?.value ?: ""
                            if (sortAscending) value else value
                        },
                    )
                if (!sortAscending) {
                    result = result.reversed()
                }
            }

            result
        }

    // Group items if groupByField is set
    val groupedItems =
        remember(processedItems, groupByField) {
            if (groupByField != null) {
                processedItems.groupBy { itemWithValues ->
                    itemWithValues.values.find { it.fieldId == groupByField!!.id }?.value ?: "(Empty)"
                }
            } else {
                mapOf("" to processedItems)
            }
        }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val context = LocalContext.current
                    val prefs = remember { PreferencesManager.getInstance(context) }
                    ConnectionStatusAction(prefs = prefs)
                    IconButton(onClick = { showManageColumnsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Columns")
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
            FloatingActionButton(
                onClick = { showAddItemDialog = true },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_item))
            }
        },
    ) { padding ->
        if (stableFields.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                // Filter/Sort/Group Controls - Always visible above table
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
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
                                Icons.Default.List,
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
                }

                // Field headers with long-press to delete and resize handles
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScrollState),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    stableFields.forEach { field ->
                        key(field.id) {
                            FieldHeader(
                                field = field,
                                width = fieldWidths[field.id] ?: 150.dp,
                                onWidthChange = { delta ->
                                    val currentWidth = fieldWidths[field.id] ?: 150.dp
                                    val newWidth = (currentWidth.value + delta).coerceIn(100f, 400f)
                                    fieldWidths[field.id] = newWidth.dp
                                },
                            )
                        }
                    }
                }

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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groupedItems.entries.forEach { (groupName, groupItems) ->
                            // Show group header if grouping is enabled
                            if (groupByField != null) {
                                item(key = "group_$groupName") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = "$groupName (${groupItems.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(12.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }

                            // Show items in the group
                            items(
                                items = groupItems,
                                key = { it.item.id },
                            ) { itemWithValues ->
                                ItemRow(
                                    fields = stableFields,
                                    fieldWidths = fieldWidths,
                                    itemWithValues = itemWithValues,
                                    scrollState = horizontalScrollState,
                                    onClick = { itemToEdit = itemWithValues },
                                )
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
            onDismiss = { showManageColumnsDialog = false },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FieldHeader(
    field: Field,
    width: Dp,
    onWidthChange: (Float) -> Unit,
) {
    val density = LocalDensity.current

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
                    modifier = Modifier.weight(1f),
                )

                // Resize handle
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    with(density) {
                                        onWidthChange(dragAmount.x.toDp().value)
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
    itemWithValues: ItemWithValues,
    scrollState: androidx.compose.foundation.ScrollState,
    onClick: () -> Unit,
) {
    // Map values by fieldId to ensure correct value-column alignment regardless of original order
    val valuesByFieldId =
        remember(itemWithValues.values) {
            itemWithValues.values.associateBy { it.fieldId }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fields.forEach { field ->
            key(field.id) {
                val value = valuesByFieldId[field.id]
                val fieldWidth = fieldWidths[field.id] ?: 150.dp

                Box(
                    modifier =
                        Modifier
                            .width(fieldWidth)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            .padding(8.dp),
                ) {
                    when (field.getType()) {
                        com.collabtable.app.data.model.FieldType.TEXT -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
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
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
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
                                                )
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(4.dp),
                                                ),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = value?.value ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        com.collabtable.app.data.model.FieldType.LOCATION -> {
                            Text(
                                text = value?.value ?: "",
                                style = MaterialTheme.typography.bodyMedium,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                )
                            } else {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodyMedium,
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
                        value =
                            when (selectedFieldType) {
                                "TEXT" -> "Text"
                                "MULTILINE_TEXT" -> "Multi-line Text"
                                "NUMBER" -> "Number"
                                "CURRENCY" -> "Currency"
                                "PERCENTAGE" -> "Percentage"
                                "DROPDOWN" -> "Dropdown"
                                "CHECKBOX" -> "Checkbox"
                                "URL" -> "URL"
                                "EMAIL" -> "Email"
                                "PHONE" -> "Phone"
                                "DATE" -> "Date"
                                "TIME" -> "Time"
                                "DATETIME" -> "Date & Time"
                                else -> "Text"
                            },
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
                        val options =
                            when (selectedFieldType) {
                                "CURRENCY" -> currency.trim()
                                "DROPDOWN" ->
                                    dropdownOptions.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .joinToString("|")
                                "AUTOCOMPLETE" ->
                                    dropdownOptions.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .joinToString("|")
                                "RATING" -> currency.trim().ifBlank { "5" }
                                else -> ""
                            }
                        onAdd(fieldName.trim(), selectedFieldType, options)
                    }
                },
                enabled =
                    fieldName.isNotBlank() &&
                        (selectedFieldType !in listOf("DROPDOWN", "AUTOCOMPLETE") || dropdownOptions.isNotBlank()),
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
    var selectedFieldType by remember { mutableStateOf(field.fieldType ?: "TEXT") }
    var dropdownOptions by remember { mutableStateOf(field.getDropdownOptions().joinToString(", ")) }
    var currency by remember { mutableStateOf(field.getCurrency()) }
    var expanded by remember { mutableStateOf(false) }

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
                        value =
                            when (selectedFieldType) {
                                "TEXT", "STRING" -> "Text"
                                "MULTILINE_TEXT" -> "Multi-line Text"
                                "NUMBER" -> "Number"
                                "CURRENCY", "PRICE" -> "Currency"
                                "PERCENTAGE" -> "Percentage"
                                "DROPDOWN" -> "Dropdown"
                                "CHECKBOX" -> "Checkbox"
                                "URL" -> "URL"
                                "EMAIL" -> "Email"
                                "PHONE" -> "Phone"
                                "DATE" -> "Date"
                                "TIME" -> "Time"
                                "DATETIME" -> "Date & Time"
                                else -> "Text"
                            },
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
                    val options =
                        when (selectedFieldType) {
                            "CURRENCY", "PRICE" -> currency.trim()
                            "DROPDOWN" ->
                                dropdownOptions.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("|")
                            "AUTOCOMPLETE" ->
                                dropdownOptions.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .joinToString("|")
                            "RATING" -> currency.trim().ifBlank { "5" }
                            else -> ""
                        }
                    onUpdate(name.trim(), selectedFieldType, options)
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
                    } else {
                        null
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
                                                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.surface
                                            },
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                    .border(
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

                Divider()

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

                Divider()

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

                Divider()

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

                Divider()

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

                    Divider()

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

                    Divider()

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

                Divider()

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
