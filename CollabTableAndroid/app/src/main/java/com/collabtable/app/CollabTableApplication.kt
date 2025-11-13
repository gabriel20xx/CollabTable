package com.collabtable.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.repository.SyncRepository
import com.collabtable.app.notifications.NotificationHelper
import com.collabtable.app.notifications.NotificationPoller
import com.collabtable.app.work.ListChangeNotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CollabTableApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize API client with saved server URL
        ApiClient.initialize(this)
        // Kick off a one-time full down-sync on cold app start if local DB is empty but server has data.
        // This complements in-screen sync logic and ensures earliest possible hydration.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Force a full down-sync on app start when server credentials are present.
                SyncRepository(this@CollabTableApplication).performSync(forceFullDown = true)
            } catch (_: Exception) {
                // Silent; UI layers will surface subsequent sync issues.
            }
        }

        // Create notification channel(s) for list events
        createNotificationChannels()

        // Schedule periodic background check for list changes to surface notifications
        scheduleListChangeWorker()

        // Start foreground notification polling loop (respects user interval)
        NotificationPoller.start(this)

        // Auto-clear notifications when app comes to foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    NotificationHelper.clearListEventNotifications(this@CollabTableApplication)
                    NotificationPoller.start(this@CollabTableApplication)
                }
                override fun onStop(owner: LifecycleOwner) {
                    NotificationPoller.stop()
                }
            },
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val listEventsChannel =
            NotificationChannel(
                NotificationHelper.CHANNEL_LIST_EVENTS,
                "List Events",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for list add, edit, and remove events"
            }
        manager.createNotificationChannel(listEventsChannel)
    }

    private fun scheduleListChangeWorker() {
        val work =
            PeriodicWorkRequestBuilder<ListChangeNotificationWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "list_change_notifier",
            ExistingPeriodicWorkPolicy.UPDATE,
            work,
        )
    }
}
