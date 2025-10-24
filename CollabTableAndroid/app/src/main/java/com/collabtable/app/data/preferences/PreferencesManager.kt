package com.collabtable.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("collab_table_prefs", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(getServerUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trim().let {
            if (it.endsWith("/")) it else "$it/"
        }
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply()
        _serverUrl.value = cleanUrl
    }

    fun isFirstRun(): Boolean {
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setIsFirstRun(isFirstRun: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_RUN, isFirstRun).apply()
    }

    fun getServerPassword(): String? {
        return prefs.getString(KEY_SERVER_PASSWORD, null)
    }

    fun setServerPassword(password: String) {
        prefs.edit().putString(KEY_SERVER_PASSWORD, password).apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_SERVER_PASSWORD = "server_password"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000/api/"
        
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
