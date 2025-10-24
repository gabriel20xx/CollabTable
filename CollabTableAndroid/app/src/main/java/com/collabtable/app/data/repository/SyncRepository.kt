package com.collabtable.app.data.repository

import android.content.Context
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.api.SyncRequest
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                Logger.i("Sync", "🔄 Starting initial sync with server")
            }

            // Gather local changes since last sync
            val localLists = database.listDao().getListsUpdatedSince(lastSync)
            val localFields = database.fieldDao().getFieldsUpdatedSince(lastSync)
            val localItems = database.itemDao().getItemsUpdatedSince(lastSync)
            val localValues = database.itemValueDao().getValuesUpdatedSince(lastSync)

            // Only log when sending data
            if (localLists.isNotEmpty() || localFields.isNotEmpty() || localItems.isNotEmpty()) {
                Logger.i("Sync", "⬆️ Sending to server:")
                if (localLists.isNotEmpty()) {
                    Logger.i("Sync", "   📋 ${localLists.size} list(s)")
                    localLists.forEach { list ->
                        val action = if (list.isDeleted) "Deleted" else "Updated"
                        Logger.i("Sync", "      $action: ${list.name}")
                    }
                }
                if (localFields.isNotEmpty()) Logger.i("Sync", "   🏷️ ${localFields.size} field(s)")
                if (localItems.isNotEmpty()) Logger.i("Sync", "   📝 ${localItems.size} item(s)")
                if (localValues.isNotEmpty()) Logger.i("Sync", "   💾 ${localValues.size} value(s)")
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

                // Only log when receiving data
                if (syncResponse.lists.isNotEmpty() || syncResponse.fields.isNotEmpty() || 
                    syncResponse.items.isNotEmpty() || syncResponse.itemValues.isNotEmpty()) {
                    Logger.i("Sync", "⬇️ Received from server:")
                    if (syncResponse.lists.isNotEmpty()) {
                        Logger.i("Sync", "   📋 ${syncResponse.lists.size} list(s)")
                        syncResponse.lists.forEach { list ->
                            if (!list.isDeleted) {
                                Logger.i("Sync", "      ${list.name}")
                            }
                        }
                    }
                    if (syncResponse.fields.isNotEmpty()) Logger.i("Sync", "   🏷️ ${syncResponse.fields.size} field(s)")
                    if (syncResponse.items.isNotEmpty()) Logger.i("Sync", "   📝 ${syncResponse.items.size} item(s)")
                    if (syncResponse.itemValues.isNotEmpty()) Logger.i("Sync", "   💾 ${syncResponse.itemValues.size} value(s)")
                }

                // Apply server changes to local database
                database.listDao().insertLists(syncResponse.lists)
                database.fieldDao().insertFields(syncResponse.fields)
                database.itemDao().insertItems(syncResponse.items)
                database.itemValueDao().insertValues(syncResponse.itemValues)

                // Update last sync timestamp
                setLastSyncTimestamp(syncResponse.serverTimestamp)

                if (isInitialSync) {
                    Logger.i("Sync", "✅ Initial sync completed")
                }

                return@withContext Result.success(Unit)
            } else {
                Logger.e("Sync", "❌ Sync failed: HTTP ${response.code()}")
                return@withContext Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Logger.e("Sync", "❌ Sync error: ${e.message}")
            return@withContext Result.failure(e)
        }
    }
}
