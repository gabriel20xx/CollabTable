package com.collabtable.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.api.SyncRequest
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = CollabTableDatabase.getDatabase(appContext)
    private val api = ApiClient.api
    private val prefs = appContext.getSharedPreferences("collab_table_prefs", Context.MODE_PRIVATE)

        @Volatile private var authBackoffUntil: Long = 0L
        @Volatile private var authBackoffMs: Long = 0L

        fun resetAuthBackoff() {
            authBackoffMs = 0L
            authBackoffUntil = 0L
        }
    // We keep separate watermarks to avoid clock-skew issues:
    // - server watermark: server-assigned timestamp for fetching server changes
    // - local watermark: device local time for selecting local changes to upload
    private fun getLastServerSyncTs(): Long {
        // Backward-compat: fall back to legacy key if new one is absent
        if (!prefs.contains("last_server_sync_ts")) {
            return prefs.getLong("last_sync_timestamp", 0)
        }
        return prefs.getLong("last_server_sync_ts", 0)
    }

    private fun setLastServerSyncTs(ts: Long) {
        prefs.edit()
            .putLong("last_server_sync_ts", ts)
            // also update legacy for safety
            .putLong("last_sync_timestamp", ts)
            .apply()
    }

    private fun getLastLocalSyncTs(): Long {
        // Backward-compat: if missing, use legacy key value
        if (!prefs.contains("last_local_sync_ts")) {
            return prefs.getLong("last_sync_timestamp", 0)
        }
        return prefs.getLong("last_local_sync_ts", 0)
    }

    private fun setLastLocalSyncTs(ts: Long) {
        prefs.edit().putLong("last_local_sync_ts", ts).apply()
    }

    suspend fun performSync(): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Prevent overlapping syncs across screens/viewmodels
            syncMutex.withLock {
            try {
                // If not authenticated, skip making network calls (e.g., after leaving server)
                val prefMgr = PreferencesManager.getInstance(appContext)
                val currentPassword = prefMgr.getServerPassword()?.trim()
                if (currentPassword.isNullOrBlank() || currentPassword == "\$password") {
                    // Quietly skip to avoid spamming server with missing Authorization
                    return@withContext Result.success(Unit)
                }
                // Respect auth backoff window to avoid spamming the server on repeated 401s
                val now = System.currentTimeMillis()
                if (authBackoffUntil > now) {
                    return@withContext Result.success(Unit)
                }
                val lastServerTs = getLastServerSyncTs()
                val lastLocalTs = getLastLocalSyncTs()
                // Capture a snapshot timestamp BEFORE reading local changes to avoid missing
                // updates that happen during an in-flight sync. We'll advance the local watermark
                // to this snapshot once the sync completes successfully.
                val localSnapshotTs = System.currentTimeMillis()
                val isInitialSync = lastServerTs == 0L
                if (isInitialSync) {
                    Logger.i("Sync", "[SYNC] Starting initial sync with server")
                }

                // Gather local changes since last sync
                val localLists = database.listDao().getListsUpdatedSince(lastLocalTs)
                    .filter { it.updatedAt in (lastLocalTs + 1)..localSnapshotTs }
                val localFields = database.fieldDao().getFieldsUpdatedSince(lastLocalTs)
                    .filter { it.updatedAt in (lastLocalTs + 1)..localSnapshotTs }
                val localItems = database.itemDao().getItemsUpdatedSince(lastLocalTs)
                    .filter { it.updatedAt in (lastLocalTs + 1)..localSnapshotTs }
                val localValues = database.itemValueDao().getValuesUpdatedSince(lastLocalTs)
                    .filter { it.updatedAt in (lastLocalTs + 1)..localSnapshotTs }

                // Only log send when there are actual local changes
                val localTotal = localLists.size + localFields.size + localItems.size + localValues.size
                if (localTotal > 0) {
                    Logger.i(
                        "Sync",
                        "[OUT] Sending changes " +
                            "(lists=${localLists.size}, fields=${localFields.size}, " +
                            "items=${localItems.size}, values=${localValues.size})",
                    )
                }

                // Send to server and get updates
                val syncRequest =
                    SyncRequest(
                        lastSyncTimestamp = lastServerTs,
                        lists = localLists,
                        fields = localFields,
                        items = localItems,
                        itemValues = localValues,
                    )

                // HTTP-only sync
                val response = api.sync(syncRequest)
                if (!response.isSuccessful) {
                    if (response.code() == 401) {
                        // Unauthorized: likely bad/missing password. Reset sync baseline and surface a clear error.
                        setLastServerSyncTs(0)
                        setLastLocalSyncTs(0)
                        // Apply exponential backoff on repeated auth failures
                        authBackoffMs = if (authBackoffMs == 0L) 2_000L else (authBackoffMs * 2).coerceAtMost(300_000L)
                        authBackoffUntil = System.currentTimeMillis() + authBackoffMs
                        return@withContext Result.failure(Exception("Unauthorized (401). Please verify server password."))
                    }
                    return@withContext Result.failure(Exception("Sync failed: ${response.code()}"))
                }
                val syncResponse = response.body()!!
                // Only log receive when there are actual server changes
                val inTotal =
                    syncResponse.lists.size +
                        syncResponse.fields.size +
                        syncResponse.items.size +
                        syncResponse.itemValues.size
                if (inTotal > 0) {
                    Logger.i(
                        "Sync",
                        "[IN] Received changes " +
                            "(lists=${syncResponse.lists.size}, fields=${syncResponse.fields.size}, " +
                            "items=${syncResponse.items.size}, values=${syncResponse.itemValues.size})",
                    )
                }

                // Apply server changes to local database atomically in correct order
                database.withTransaction {
                    // 1) Upsert lists first, preserving local orderIndex when present
                    val localIdToOrder = database.listDao().getListIdsAndOrder().associate { it.id to it.orderIndex }
                    // Build a map of local updatedAt to avoid overwriting newer local changes with older server data
                    val localUpdatedMap = database.listDao().getListsUpdatedSince(0).associateBy({ it.id }, { it.updatedAt })

                    val listsPreservingOrder =
                        syncResponse.lists
                            .filter { incoming ->
                                val localUpdated = localUpdatedMap[incoming.id]
                                localUpdated == null || incoming.updatedAt >= localUpdated
                            }
                            .map { incoming ->
                                val localOrder = localIdToOrder[incoming.id]
                                if (localOrder != null) incoming.copy(orderIndex = localOrder) else incoming
                            }

                    if (listsPreservingOrder.isNotEmpty()) {
                        database.listDao().insertLists(listsPreservingOrder)
                    }

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

                // Update watermarks: server ts from response, local ts from device clock now
                setLastServerSyncTs(syncResponse.serverTimestamp)
                // Advance local watermark to the snapshot taken before reading local changes.
                // This avoids missing updates that occurred while the sync was in-flight.
                setLastLocalSyncTs(localSnapshotTs)

                if (isInitialSync) {
                    Logger.i("Sync", "[SYNC] Initial sync completed")
                }

                // Successful sync clears any previous auth backoff
                resetAuthBackoff()

                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                // If WS indicated unauthorized, align behavior with HTTP path
                if (e.message?.contains("Unauthorized") == true) {
                    setLastServerSyncTs(0)
                    setLastLocalSyncTs(0)
                }
                return@withContext Result.failure(e)
            }
            }
        }

    companion object {
        private val syncMutex = Mutex()
    }
}
