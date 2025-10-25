package com.collabtable.app.data.repository

import android.content.Context
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.api.SyncRequest
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

class SyncRepository(context: Context) {
    private val database = CollabTableDatabase.getDatabase(context)
    private val api = ApiClient.api
    private val prefs = context.getSharedPreferences("collab_table_prefs", Context.MODE_PRIVATE)

    private fun getLastSyncTimestamp(): Long {
        return prefs.getLong("last_sync_timestamp", 0)
    }

    private fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    suspend fun performSync(): Result<Unit> = withContext(Dispatchers.IO) {
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
                    "[OUT] Sending changes (lists=${localLists.size}, fields=${localFields.size}, items=${localItems.size}, values=${localValues.size})"
                )
            }

            // Send to server and get updates
            val syncRequest = SyncRequest(
                lastSyncTimestamp = lastSync,
                lists = localLists,
                fields = localFields,
                items = localItems,
                itemValues = localValues
            )

            val response = api.sync(syncRequest)

            if (response.isSuccessful) {
                val syncResponse = response.body()!!

                // Only log receive when there are actual server changes
                val inTotal = syncResponse.lists.size + syncResponse.fields.size + syncResponse.items.size + syncResponse.itemValues.size
                if (inTotal > 0) {
                    Logger.i(
                        "Sync",
                        "[IN] Received changes (lists=${syncResponse.lists.size}, fields=${syncResponse.fields.size}, items=${syncResponse.items.size}, values=${syncResponse.itemValues.size})"
                    )
                }

                // Apply server changes to local database atomically in correct order
                database.withTransaction {
                    database.listDao().insertLists(syncResponse.lists)
                    database.fieldDao().insertFields(syncResponse.fields)
                    database.itemDao().insertItems(syncResponse.items)

                    // Filter item values to only those whose parents exist locally to avoid FK violations
                    val existingItemIds = database.itemDao().getAllItemIds().toSet()
                    val existingFieldIds = database.fieldDao().getAllFieldIds().toSet()
                    val filteredValues = syncResponse.itemValues.filter { v ->
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
            } else {
                Logger.e("Sync", "[ERROR] Sync failed: HTTP ${response.code()}")
                return@withContext Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Logger.e("Sync", "[ERROR] Sync error: ${e.message}")
            return@withContext Result.failure(e)
        }
    }
}
