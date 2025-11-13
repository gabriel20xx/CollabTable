package com.collabtable.app.ui.screens

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.model.Field
import com.collabtable.app.data.model.Item
import com.collabtable.app.data.model.ItemValue
import com.collabtable.app.data.model.ItemWithValues
import com.collabtable.app.data.repository.SyncRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class ListDetailViewModel(
    private val database: CollabTableDatabase,
    private val listId: String,
    private val context: Context,
) : ViewModel() {
    private val _list = MutableStateFlow<CollabList?>(null)
    val list: StateFlow<CollabList?> = _list.asStateFlow()

    private val _fields = MutableStateFlow<List<Field>>(emptyList())
    val fields: StateFlow<List<Field>> = _fields.asStateFlow()

    private val _items = MutableStateFlow<List<ItemWithValues>>(emptyList())
    val items: StateFlow<List<ItemWithValues>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val syncRepository = SyncRepository(context)

    init {
        loadListData()
        startPeriodicSync()
    }

    private fun loadListData() {
        // Track first emissions; show content once fields + items arrive (list meta can lag)
        var hasList = false
        var hasFields = false
        var hasItems = false

        fun maybeLoaded() {
            if (hasFields && hasItems) {
                _isLoading.value = false
            }
        }
        viewModelScope.launch {
            database
                .listDao()
                .getListWithFields(listId)
                .debounce(75)
                .collect { listWithFields ->
                    _list.value = listWithFields?.list
                    if (!hasList) {
                        hasList = true
                        // list not required to render rows; don't gate loading on it
                    }
                }
        }

        viewModelScope.launch {
            database
                .itemDao()
                .getItemsWithValuesForList(listId)
                .debounce(75)
                .collect { itemsData ->
                    _items.value = itemsData
                    if (!hasItems) {
                        hasItems = true
                        maybeLoaded()
                    }
                }
        }

        viewModelScope.launch {
            database
                .fieldDao()
                .getFieldsForList(listId)
                .debounce(75)
                .collect { fieldsData ->
                    _fields.value = fieldsData
                    if (!hasFields) {
                        hasFields = true
                        maybeLoaded()
                    }
                }
        }

        // Fallback: ensure loading overlay disappears even if one of flows stalls
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)
            if (_isLoading.value) _isLoading.value = false
        }
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            val prefs =
                com.collabtable.app.data.preferences.PreferencesManager
                    .getInstance(context)
            while (true) {
                performSync()
                val delayMs = prefs.getSyncPollIntervalMs()
                kotlinx.coroutines.delay(delayMs)
            }
        }
    }

    private suspend fun performSync() {
        syncRepository.performSync()
    }

    private fun isInForeground(): Boolean {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        return state.isAtLeast(Lifecycle.State.STARTED)
    }

    // Local changes should not notify this same device; remote-change notification (future) will be triggered elsewhere.
    private fun maybeNotifyListContentUpdated() { /* intentionally disabled for local-origin changes */ }

    fun renameList(newName: String) {
        viewModelScope.launch {
            val currentList = _list.value
            if (currentList != null && newName.isNotBlank()) {
                database.listDao().updateList(
                    currentList.copy(
                        name = newName.trim(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                performSync()
            }
        }
    }

    fun addField(
        name: String,
        fieldType: String = "STRING",
        fieldOptions: String = "",
    ) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val maxOrder = _fields.value.maxOfOrNull { it.order } ?: -1
            val newField =
                Field(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    name = name,
                    fieldType = fieldType,
                    fieldOptions = fieldOptions,
                    order = maxOrder + 1,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )

            // Create empty ItemValue entries for this new field for all existing items
            val existingItems = _items.value
            val newValues =
                if (existingItems.isNotEmpty()) {
                    existingItems.map { itemWithValues ->
                        ItemValue(
                            id = UUID.randomUUID().toString(),
                            itemId = itemWithValues.item.id,
                            fieldId = newField.id,
                            value = "",
                            updatedAt = timestamp,
                        )
                    }
                } else {
                    emptyList()
                }

            // Insert atomically to avoid intermediate inconsistent states and bump list.updatedAt
            database.withTransaction {
                database.fieldDao().insertField(newField)
                if (newValues.isNotEmpty()) {
                    database.itemValueDao().insertValues(newValues)
                }
                database.listDao().getListById(listId)?.let { l ->
                    database.listDao().updateList(l.copy(updatedAt = timestamp))
                }
            }

            performSync() // no local notification
        }
    }

    fun deleteField(fieldId: String) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            database.withTransaction {
                // Soft delete the field
                database.fieldDao().softDeleteField(fieldId, ts)
                // Determine remaining active fields by consulting the current state flow (already up-to-date via collector)
                // Fields state already excludes soft-deleted ones (query filters isDeleted = 0), so just check size after removal
                val remainingFields = _fields.value
                if (remainingFields.isEmpty()) {
                    // Last column deleted: soft-delete all items for this list to clear table content
                    database.itemDao().softDeleteItemsByList(listId, ts)
                }
                database.listDao().getListById(listId)?.let { l ->
                    database.listDao().updateList(l.copy(updatedAt = ts))
                }
            }
            performSync() // no local notification
        }
    }

    fun updateField(
        fieldId: String,
        name: String,
        fieldType: String,
        fieldOptions: String,
    ) {
        viewModelScope.launch {
            val field = database.fieldDao().getFieldById(fieldId)
            if (field != null) {
                val ts = System.currentTimeMillis()
                database.withTransaction {
                    database.fieldDao().updateField(
                        field.copy(
                            name = name,
                            fieldType = fieldType,
                            fieldOptions = fieldOptions,
                            updatedAt = ts,
                        ),
                    )
                    database.listDao().getListById(listId)?.let { l ->
                        database.listDao().updateList(l.copy(updatedAt = ts))
                    }
                }
                performSync() // no local notification
            }
        }
    }

    fun updateFieldAlignment(
        fieldId: String,
        alignment: String,
    ) {
        viewModelScope.launch {
            val field = database.fieldDao().getFieldById(fieldId)
            if (field != null) {
                val ts = System.currentTimeMillis()
                val normalized =
                    when (alignment.lowercase()) {
                        "center" -> "center"
                        "end", "right" -> "end"
                        else -> "start"
                    }
                database.withTransaction {
                    database.fieldDao().updateField(
                        field.copy(
                            alignment = normalized,
                            updatedAt = ts,
                        ),
                    )
                    database.listDao().getListById(listId)?.let { l ->
                        database.listDao().updateList(l.copy(updatedAt = ts))
                    }
                }
                performSync()
            }
        }
    }

    fun addItem() {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val newItem =
                Item(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
            // Insert item and its values atomically
            database.withTransaction {
                database.itemDao().insertItem(newItem)
                // Create empty values for each field
                val values =
                    _fields.value.map { field ->
                        ItemValue(
                            id = UUID.randomUUID().toString(),
                            itemId = newItem.id,
                            fieldId = field.id,
                            value = "",
                            updatedAt = timestamp,
                        )
                    }
                if (values.isNotEmpty()) {
                    database.itemValueDao().insertValues(values)
                }
                database.listDao().getListById(listId)?.let { l ->
                    database.listDao().updateList(l.copy(updatedAt = timestamp))
                }
            }
            performSync() // no local notification
        }
    }

    fun addItemWithValues(fieldValues: Map<String, String>) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val newItem =
                Item(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
            // Insert item and provided values atomically
            database.withTransaction {
                database.itemDao().insertItem(newItem)
                // Create values for each field with the provided values
                val values =
                    _fields.value.map { field ->
                        ItemValue(
                            id = UUID.randomUUID().toString(),
                            itemId = newItem.id,
                            fieldId = field.id,
                            value = fieldValues[field.id] ?: "",
                            updatedAt = timestamp,
                        )
                    }
                if (values.isNotEmpty()) {
                    database.itemValueDao().insertValues(values)
                }
                database.listDao().getListById(listId)?.let { l ->
                    database.listDao().updateList(l.copy(updatedAt = timestamp))
                }
            }
            performSync() // no local notification
        }
    }

    fun updateItemValue(
        itemValueId: String,
        newValue: String,
    ) {
        viewModelScope.launch {
            val itemValue = database.itemValueDao().getValueById(itemValueId)
            if (itemValue != null) {
                val ts = System.currentTimeMillis()
                database.withTransaction {
                    database.itemValueDao().updateValue(
                        itemValue.copy(
                            value = newValue,
                            updatedAt = ts,
                        ),
                    )
                    database.listDao().getListById(listId)?.let { l ->
                        database.listDao().updateList(l.copy(updatedAt = ts))
                    }
                }
                performSync()
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            database.withTransaction {
                database.itemDao().softDeleteItem(itemId, ts)
                database.listDao().getListById(listId)?.let { l ->
                    database.listDao().updateList(l.copy(updatedAt = ts))
                }
            }
            performSync()
            maybeNotifyListContentUpdated()
        }
    }

    fun reorderFields(reorderedFields: List<Field>) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            // Reorder within a transaction and bump list.updatedAt
            database.withTransaction {
                database.fieldDao().reorderFieldsInTransaction(reorderedFields, timestamp)
                database.listDao().getListById(listId)?.let { l ->
                    database.listDao().updateList(l.copy(updatedAt = timestamp))
                }
            }
            performSync()
        }
    }
}
