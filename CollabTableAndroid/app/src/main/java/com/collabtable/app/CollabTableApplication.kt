package com.collabtable.app

import android.app.Application
import com.collabtable.app.data.api.ApiClient
import com.collabtable.app.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    }
}
