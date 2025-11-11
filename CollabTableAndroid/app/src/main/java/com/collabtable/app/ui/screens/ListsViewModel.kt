package com.collabtable.app.ui.screens

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.repository.SyncRepository
import com.collabtable.app.notifications.NotificationHelper
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

class ListsViewModel(
    private val database: CollabTableDatabase,
    private val context: Context,
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
            val prefs =
                com.collabtable.app.data.preferences.PreferencesManager
                    .getInstance(context)
            val listsFlow = database.listDao().getAllLists()
            val sortFlow = prefs.sortOrder

            combine(listsFlow, sortFlow) { listData, sortOrder ->
                val anyManual = listData.any { it.orderIndex != null }
                if (anyManual) {
                    listData.sortedWith(compareBy(nullsLast()) { it.orderIndex })
                } else {
                    when (sortOrder) {
                        com.collabtable.app.data.preferences.PreferencesManager.SORT_NAME_ASC ->
                            listData.sortedBy { it.name.lowercase() }
                        com.collabtable.app.data.preferences.PreferencesManager.SORT_NAME_DESC ->
                            listData.sortedByDescending { it.name.lowercase() }
                        com.collabtable.app.data.preferences.PreferencesManager.SORT_UPDATED_ASC ->
                            listData.sortedBy { it.updatedAt }
                        else ->
                            listData.sortedByDescending { it.updatedAt }
                    }
                }
            }.collect { sorted ->
                _lists.value = sorted
            }
        }
    }

    fun reorder(
        fromIndex: Int,
        toIndex: Int,
    ) {
        viewModelScope.launch {
            val current = _lists.value.toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) return@launch

            // Ensure we have a baseline orderIndex to start from if null
            if (current.all { it.orderIndex == null }) {
                current.forEachIndexed { idx, item ->
                    database.listDao().updateListOrderIndex(item.id, idx.toLong())
                }
            }

            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)

            // Renumber sequentially to avoid large gaps
            val newOrder: List<Pair<String, Long>> = current.mapIndexed { idx, l -> l.id to idx.toLong() }
            database.listDao().updateOrderIndexes(newOrder)

            // Trigger a fresh load (flows will emit on DB change)
        }
    }

    fun commitReorder(newOrderIds: List<String>) {
        viewModelScope.launch {
            val pairs = newOrderIds.mapIndexed { idx, id -> id to idx.toLong() }
            database.listDao().updateOrderIndexes(pairs)
        }
    }

    private suspend fun startPeriodicSync() {
        val prefs =
            com.collabtable.app.data.preferences.PreferencesManager
                .getInstance(context)
        while (true) {
            val delayMs = prefs.getSyncPollIntervalMs()
            kotlinx.coroutines.delay(delayMs)
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
            val newList =
                CollabList(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
            database.listDao().insertList(newList)
            // Sync immediately after creating
            performSync()
            maybeNotifyListAdded(newList)
        }
    }

    fun renameList(
        listId: String,
        newName: String,
    ) {
        viewModelScope.launch {
            val list = database.listDao().getListById(listId)
            if (list != null && newName.isNotBlank()) {
                Logger.i("Tables", "✏️ Renaming table: \"${list.name}\" → \"$newName\"")
                database.listDao().updateList(
                    list.copy(
                        name = newName.trim(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                // Sync immediately after renaming
                performSync()
                maybeNotifyListEdited(list.copy(name = newName.trim()))
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            val list = database.listDao().getListById(listId)
            if (list != null) {
                Logger.i("Tables", "🗑️ Deleting table: \"${list.name}\"")
                val ts = System.currentTimeMillis()
                // Soft-delete the list, and cascade soft-deletes to its fields and items so sync uploads tombstones
                database.listDao().softDeleteList(listId, ts)
                // Cascade locally to ensure children are marked deleted and uploaded on next sync
                database.fieldDao().softDeleteFieldsByList(listId, ts)
                database.itemDao().softDeleteItemsByList(listId, ts)
                // Sync immediately after deleting
                performSync()
                maybeNotifyListRemoved(list)
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

    private fun isInForeground(): Boolean {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        return state.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun maybeNotifyListAdded(list: CollabList) {
        val prefs =
            com.collabtable.app.data.preferences.PreferencesManager
                .getInstance(context)
        if (prefs.notifyListAdded.value && !isInForeground()) {
            NotificationHelper.showListAdded(context, list.id, list.name)
        }
    }

    private fun maybeNotifyListEdited(list: CollabList) {
        val prefs =
            com.collabtable.app.data.preferences.PreferencesManager
                .getInstance(context)
        if (prefs.notifyListEdited.value && !isInForeground()) {
            NotificationHelper.showListEdited(context, list.id, list.name)
        }
    }

    private fun maybeNotifyListRemoved(list: CollabList) {
        val prefs =
            com.collabtable.app.data.preferences.PreferencesManager
                .getInstance(context)
        if (prefs.notifyListRemoved.value && !isInForeground()) {
            NotificationHelper.showListRemoved(context, list.id, list.name)
        }
    }
}
