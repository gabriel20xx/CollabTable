package com.collabtable.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.notifications.NotificationHelper

class ListChangeNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val prefs = PreferencesManager.getInstance(applicationContext)
        val db = CollabTableDatabase.getDatabase(applicationContext)

        val since = prefs.getLastListNotifyCheckTimestamp()
        val now = System.currentTimeMillis()
        return try {
            val changed = db.listDao().getListsUpdatedSince(since)
            if (changed.isNotEmpty()) {
                changed.forEach { list ->
                    emitNotificationsFor(list, prefs)
                }
            }
            // Update checkpoint regardless to avoid duplicate spam
            prefs.setLastListNotifyCheckTimestamp(now)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun emitNotificationsFor(
        list: CollabList,
        prefs: PreferencesManager,
    ) {
        // Determine event type
        when {
            list.isDeleted -> {
                if (prefs.notifyListRemoved.value) {
                    NotificationHelper.showListRemoved(applicationContext, list.id, list.name)
                }
            }
            list.createdAt == list.updatedAt -> {
                if (prefs.notifyListAdded.value) {
                    NotificationHelper.showListAdded(applicationContext, list.id, list.name)
                }
            }
            else -> {
                if (prefs.notifyListEdited.value) {
                    NotificationHelper.showListEdited(applicationContext, list.id, list.name)
                }
            }
        }
    }
}
