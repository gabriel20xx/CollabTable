package com.collabtable.app.notifications

import android.content.Context
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.api.CollabTableApi
import com.collabtable.app.data.api.NotificationEvent
import com.collabtable.app.data.database.CollabTableDatabase
import com.collabtable.app.data.preferences.PreferencesManager
import com.collabtable.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object NotificationPoller {
    @Volatile private var job: Job? = null

    fun start(context: Context) {
        if (job?.isActive == true) return
        val appCtx = context.applicationContext
        val prefs = PreferencesManager.getInstance(appCtx)
        val api = ApiClient.api
        val db = CollabTableDatabase.getDatabase(appCtx)
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // Skip if password missing (auth disabled)
                    val pwd = prefs.getServerPassword()?.trim()
                    if (pwd.isNullOrBlank() || pwd == "\$password") {
                        delay(2_000)
                        continue
                    }
                    val since = prefs.getLastListNotifyCheckTimestamp()
                    val resp = api.pollNotifications(since)
                    if (resp.isSuccessful) {
                        val body = resp.body()
                        val events = body?.notifications.orEmpty()
                        if (events.isNotEmpty()) {
                            // Map and emit based on preferences
                            events.forEach { ev ->
                                val listId = ev.listId ?: return@forEach
                                val list = db.listDao().getListById(listId) ?: return@forEach
                                handleEvent(appCtx, ev, list.name, prefs)
                            }
                        }
                        // Advance checkpoint to server timestamp to avoid re-processing
                        val stamp = body?.serverTimestamp ?: System.currentTimeMillis()
                        prefs.setLastListNotifyCheckTimestamp(stamp)
                    } else if (resp.code() == 401) {
                        // Unauthorized; back off by advancing timestamp minimally to avoid tight loop
                        delay(5_000)
                    }
                } catch (e: Exception) {
                    try { Logger.w("NotifPoll", "Polling failed: ${e.message}") } catch (_: Exception) {}
                    // brief backoff
                    delay(1_500)
                }
                val interval = prefs.syncPollIntervalMs.value
                delay(interval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun handleEvent(
        context: Context,
        ev: NotificationEvent,
        listName: String,
        prefs: PreferencesManager,
    ) {
        // Respect notification switches by grouping semantics
        when (ev.entityType.lowercase()) {
            "list" -> when (ev.eventType.lowercase()) {
                "created" -> if (prefs.notifyListAdded.value) NotificationHelper.showListAdded(context, ev.listId ?: "", listName)
                "updated" -> if (prefs.notifyListEdited.value) NotificationHelper.showListEdited(context, ev.listId ?: "", listName)
                "deleted" -> if (prefs.notifyListRemoved.value) NotificationHelper.showListRemoved(context, ev.listId ?: "", listName)
            }
            // Treat fields/items/value changes as content updates
            "field", "item", "value" -> if (prefs.notifyListContentUpdated.value) {
                NotificationHelper.showListContentUpdated(context, ev.listId ?: "", listName)
            }
            else -> when (ev.eventType.lowercase()) {
                "listcontentupdated" -> if (prefs.notifyListContentUpdated.value) {
                    NotificationHelper.showListContentUpdated(context, ev.listId ?: "", listName)
                }
            }
        }
    }
}
