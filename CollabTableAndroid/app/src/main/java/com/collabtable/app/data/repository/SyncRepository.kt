package com.collabtable.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.api.SyncRequest
import com.collabtable.app.data.api.WebSocketSyncClient
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = CollabTableDatabase.getDatabase(appContext)
    private val api = ApiClient.api
    private val prefs = appContext.getSharedPreferences("collab_table_prefs", Context.MODE_PRIVATE)

    private fun getLastSyncTimestamp(): Long {
        return prefs.getLong("last_sync_timestamp", 0)
    }

    private fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    suspend fun performSync(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val lastSync = getLastSyncTimestamp()
                val isInitialSync = lastSync == 0L
                if (isInitialSync) {
                    Logger.i("Sync", "[SYNC] Starting initial sync with server")
                }

                // Gather local changes since last sync
                val localLists = database.listDao().getListsUpdatedSince(lastSync)
                val localFields = database.fieldDao().getFieldsUpdatedSince(lastSync)
                val localItems = database.itemDao().getItemsUpdatedSince(lastSync)
                val localValues = database.itemValueDao().getValuesUpdatedSince(lastSync)

                // Only log send when there are actual local changes
                val localTotal = localLists.size + localFields.size + localItems.size + localValues.size
                if (localTotal > 0) {
                    Logger.i(
                        "Sync",
                        "[OUT] Sending changes (lists=${localLists.size}, fields=${localFields.size}, items=${localItems.size}, values=${localValues.size})",
                    )
                }

                // Send to server and get updates
                val syncRequest =
                    SyncRequest(
                        lastSyncTimestamp = lastSync,
                        lists = localLists,
                        fields = localFields,
                        items = localItems,
                        itemValues = localValues,
                    )

                // Try WebSocket first; on failure, fall back to HTTP
                val wsResult = WebSocketSyncClient.sync(appContext, syncRequest)
                val syncResponse =
                    if (wsResult.isSuccess) {
                        wsResult.getOrThrow()
                    } else {
                        val response = api.sync(syncRequest)
                        if (!response.isSuccessful) {
                            if (response.code() == 401) {
                                // Unauthorized: likely bad/missing password. Reset sync baseline and surface a clear error.
                                Logger.e("Sync", "[ERROR] Unauthorized (401). Check server password in Settings.")
                                setLastSyncTimestamp(0)
                                return@withContext Result.failure(Exception("Unauthorized (401). Please verify server password."))
                            }
                            Logger.e("Sync", "[ERROR] Sync failed: HTTP ${response.code()}")
                            return@withContext Result.failure(Exception("Sync failed: ${response.code()}"))
                        }
                        response.body()!!
                    }
                // Only log receive when there are actual server changes
                val inTotal = syncResponse.lists.size + syncResponse.fields.size + syncResponse.items.size + syncResponse.itemValues.size
                if (inTotal > 0) {
                    Logger.i(
                        "Sync",
                        "[IN] Received changes (lists=${syncResponse.lists.size}, fields=${syncResponse.fields.size}, items=${syncResponse.items.size}, values=${syncResponse.itemValues.size})",
                    )
                }

                // Apply server changes to local database atomically in correct order
                database.withTransaction {
                    // 1) Upsert lists first, preserving local orderIndex when present
                    val idOrderMap = database.listDao().getListIdsAndOrder().associate { it.id to it.orderIndex }
                    val listsPreservingOrder = syncResponse.lists.map { incoming ->
                        val localOrder = idOrderMap[incoming.id]
                        if (localOrder != null) incoming.copy(orderIndex = localOrder) else incoming
                    }
                    database.listDao().insertLists(listsPreservingOrder)

                    // Build set of existing list ids to guard child inserts
                    val existingListIds = database.listDao().getAllListIds().toSet()

                    // 2) Filter and upsert fields whose parent list exists
                    val filteredFields = syncResponse.fields.filter { f -> f.listId in existingListIds }
                    if (filteredFields.size != syncResponse.fields.size) {
                        val dropped = syncResponse.fields.size - filteredFields.size
                        Logger.w("Sync", "Dropping $dropped field(s) with missing parent list to avoid FK errors")
                    }
                    if (filteredFields.isNotEmpty()) {
                        database.fieldDao().insertFields(filteredFields)
                    }

                    // 3) Filter and upsert items whose parent list exists
                    val filteredItems = syncResponse.items.filter { i -> i.listId in existingListIds }
                    if (filteredItems.size != syncResponse.items.size) {
                        val dropped = syncResponse.items.size - filteredItems.size
                        Logger.w("Sync", "Dropping $dropped item(s) with missing parent list to avoid FK errors")
                    }
                    if (filteredItems.isNotEmpty()) {
                        database.itemDao().insertItems(filteredItems)
                    }

                    // 4) Filter item values to only those whose parents (item and field) exist locally
                    val existingItemIds = database.itemDao().getAllItemIds().toSet()
                    val existingFieldIds = database.fieldDao().getAllFieldIds().toSet()
                    val filteredValues =
                        syncResponse.itemValues.filter { v ->
                            v.itemId in existingItemIds && v.fieldId in existingFieldIds
                        }
                    if (filteredValues.size != syncResponse.itemValues.size) {
                        val dropped = syncResponse.itemValues.size - filteredValues.size
                        Logger.w("Sync", "Dropping $dropped item value(s) with missing parents to avoid FK errors")
                    }
                    if (filteredValues.isNotEmpty()) {
                        database.itemValueDao().insertValues(filteredValues)
                    }
                }

                // Update last sync timestamp
                setLastSyncTimestamp(syncResponse.serverTimestamp)

                if (isInitialSync) {
                    Logger.i("Sync", "[SYNC] Initial sync completed")
                }

                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                // If WS indicated unauthorized, align behavior with HTTP path
                if (e.message?.contains("Unauthorized") == true) {
                    setLastSyncTimestamp(0)
                }
                Logger.e("Sync", "[ERROR] Sync error: ${e.message}")
                return@withContext Result.failure(e)
            }
        }
}
