package com.collabtable.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.repository.SyncRepository
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ListsViewModel(
    private val database: CollabTableDatabase,
    private val context: Context
) : ViewModel() {
    private val _lists = MutableStateFlow<List<CollabList>>(emptyList())
    val lists: StateFlow<List<CollabList>> = _lists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val syncRepository = SyncRepository(context)

    init {
        loadLists()
        // Perform initial sync immediately on startup, then start periodic sync
        viewModelScope.launch {
            _isLoading.value = true
            performSync()
            _isLoading.value = false
            startPeriodicSync()
        }
    }

    private fun loadLists() {
        viewModelScope.launch {
            database.listDao().getAllLists().collect { listData ->
                _lists.value = listData
            }
        }
    }
    
    private suspend fun startPeriodicSync() {
        while (true) {
            kotlinx.coroutines.delay(5000) // Wait 5 seconds before next sync
            performSync()
        }
    }
    
    private suspend fun performSync() {
        val result = syncRepository.performSync()
        result.onFailure { error ->
            Logger.e("Tables", "❌ Sync failed: ${error.message}")
        }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            Logger.i("Tables", "➕ Creating table: \"$name\"")
            val timestamp = System.currentTimeMillis()
            val newList = CollabList(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            database.listDao().insertList(newList)
            // Sync immediately after creating
            performSync()
        }
    }

    fun renameList(listId: String, newName: String) {
        viewModelScope.launch {
            val list = database.listDao().getListById(listId)
            if (list != null && newName.isNotBlank()) {
                Logger.i("Tables", "✏️ Renaming table: \"${list.name}\" → \"$newName\"")
                database.listDao().updateList(
                    list.copy(
                        name = newName.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                // Sync immediately after renaming
                performSync()
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            val list = database.listDao().getListById(listId)
            if (list != null) {
                Logger.i("Tables", "🗑️ Deleting table: \"${list.name}\"")
                database.listDao().softDeleteList(listId, System.currentTimeMillis())
                // Sync immediately after deleting
                performSync()
            }
        }
    }
    
    fun manualSync() {
        viewModelScope.launch {
            Logger.i("Tables", "🔄 Manual sync requested")
            _isLoading.value = true
            performSync()
            _isLoading.value = false
        }
    }
}
