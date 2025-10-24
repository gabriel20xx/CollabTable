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
            Logger.d("Sync", "Starting sync with lastSyncTimestamp: $lastSync")

            // Gather local changes since last sync
            val localLists = database.listDao().getListsUpdatedSince(lastSync)
            val localFields = database.fieldDao().getFieldsUpdatedSince(lastSync)
            val localItems = database.itemDao().getItemsUpdatedSince(lastSync)
            val localValues = database.itemValueDao().getValuesUpdatedSince(lastSync)

            Logger.i("Sync", "Sending to server: ${localLists.size} lists, ${localFields.size} fields, ${localItems.size} items")
            
            // Log details of lists being sent
            localLists.forEach { list ->
                Logger.d("Sync", "  List: ${list.id} - ${list.name} (updated: ${list.updatedAt})")
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

                Logger.i("Sync", "Received from server: ${syncResponse.lists.size} lists, ${syncResponse.fields.size} fields, ${syncResponse.items.size} items")

                // Log details of lists being received
                syncResponse.lists.forEach { list ->
                    Logger.d("Sync", "  Received list: ${list.id} - ${list.name} (updated: ${list.updatedAt}, deleted: ${list.isDeleted})")
                }

                // Apply server changes to local database
                Logger.d("Sync", "Inserting ${syncResponse.lists.size} lists into local database")
                database.listDao().insertLists(syncResponse.lists)
                
                Logger.d("Sync", "Inserting ${syncResponse.fields.size} fields into local database")
                database.fieldDao().insertFields(syncResponse.fields)
                
                Logger.d("Sync", "Inserting ${syncResponse.items.size} items into local database")
                database.itemDao().insertItems(syncResponse.items)
                
                Logger.d("Sync", "Inserting ${syncResponse.itemValues.size} item values into local database")
                database.itemValueDao().insertValues(syncResponse.itemValues)

                // Verify what was actually saved
                val savedLists = database.listDao().getListsUpdatedSince(0)
                Logger.d("Sync", "After insert, total lists in DB: ${savedLists.size}")
                savedLists.forEach { list ->
                    Logger.d("Sync", "  DB list: ${list.id} - ${list.name} (deleted: ${list.isDeleted})")
                }

                // Update last sync timestamp
                setLastSyncTimestamp(syncResponse.serverTimestamp)

                Logger.i("Sync", "Sync completed successfully. Timestamp updated to: ${syncResponse.serverTimestamp}")
                return@withContext Result.success(Unit)
            } else {
                Logger.e("Sync", "Sync failed with code: ${response.code()}, message: ${response.message()}")
                return@withContext Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Logger.e("Sync", "Sync error", e)
            return@withContext Result.failure(e)
        }
    }
}
