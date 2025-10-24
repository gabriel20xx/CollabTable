package com.collabtable.app

import android.app.Application
import com.collabtable.app.data.api.ApiClient

class CollabTableApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize API client with saved server URL
        ApiClient.initialize(this)
    }
}
