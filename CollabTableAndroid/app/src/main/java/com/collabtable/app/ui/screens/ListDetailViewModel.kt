package com.collabtable.app.ui.screens

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.UUID

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

    private val syncRepository = SyncRepository(context)

    init {
        loadListData()
        startPeriodicSync()
    }

    private fun loadListData() {
        viewModelScope.launch {
            database.listDao().getListWithFields(listId)
                .debounce(75)
                .collect { listWithFields ->
                    _list.value = listWithFields?.list
                }
        }

        viewModelScope.launch {
            database.itemDao().getItemsWithValuesForList(listId).collect { itemsData ->
                _items.value = itemsData
            }
        }

        viewModelScope.launch {
            database.fieldDao().getFieldsForList(listId)
                .debounce(75)
                .collect { fieldsData ->
                    _fields.value = fieldsData
                }
        }
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            while (true) {
                performSync()
                kotlinx.coroutines.delay(5000) // Sync every 5 seconds
            }
        }
    }

    private suspend fun performSync() {
        syncRepository.performSync()
    }

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

            // Insert atomically to avoid intermediate inconsistent states
            database.withTransaction {
                database.fieldDao().insertField(newField)
                if (newValues.isNotEmpty()) {
                    database.itemValueDao().insertValues(newValues)
                }
            }

            performSync()
        }
    }

    fun deleteField(fieldId: String) {
        viewModelScope.launch {
            database.fieldDao().softDeleteField(fieldId, System.currentTimeMillis())
            performSync()
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
                database.fieldDao().updateField(
                    field.copy(
                        name = name,
                        fieldType = fieldType,
                        fieldOptions = fieldOptions,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
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
            }
            performSync()
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
            }
            performSync()
        }
    }

    fun updateItemValue(
        itemValueId: String,
        newValue: String,
    ) {
        viewModelScope.launch {
            val itemValue = database.itemValueDao().getValueById(itemValueId)
            if (itemValue != null) {
                database.itemValueDao().updateValue(
                    itemValue.copy(
                        value = newValue,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                performSync()
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            database.itemDao().softDeleteItem(itemId, System.currentTimeMillis())
            performSync()
        }
    }

    fun reorderFields(reorderedFields: List<Field>) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            // Use DAO-level transaction for clarity
            database.fieldDao().reorderFieldsInTransaction(reorderedFields, timestamp)
            performSync()
        }
    }
}
