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

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(isDynamicColorEnabled())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _amoledDark = MutableStateFlow(isAmoledDarkEnabled())
    val amoledDark: StateFlow<Boolean> = _amoledDark.asStateFlow()

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        val raw = url.trim()
        if (raw.isBlank()) {
            // Remove the key entirely to fall back to default URL on reads
            prefs.edit().remove(KEY_SERVER_URL).apply()
            _serverUrl.value = getServerUrl()
            return
        }
        val cleanUrl = raw.let { if (it.endsWith("/")) it else "$it/" }
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
        if (password.isBlank()) {
            prefs.edit().remove(KEY_SERVER_PASSWORD).apply()
        } else {
            prefs.edit().putString(KEY_SERVER_PASSWORD, password).apply()
        }
    }

    // Clear sync-related state so the next sync starts from scratch
    fun clearSyncState() {
        prefs.edit().remove(KEY_LAST_SYNC_TIMESTAMP).apply()
    }

    fun clearServerSettings() {
        prefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_PASSWORD)
            .remove(KEY_LAST_SYNC_TIMESTAMP)
            .apply()
        _serverUrl.value = getServerUrl()
    }

    // Theme settings
    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM) ?: THEME_MODE_SYSTEM
    }

    fun setThemeMode(mode: String) {
        val normalized =
            when (mode.lowercase()) {
                THEME_MODE_LIGHT, THEME_MODE_DARK, THEME_MODE_SYSTEM -> mode.lowercase()
                else -> THEME_MODE_SYSTEM
            }
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
        _themeMode.value = normalized
    }

    fun isDynamicColorEnabled(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun isAmoledDarkEnabled(): Boolean {
        return prefs.getBoolean(KEY_AMOLED_DARK, false)
    }

    fun setAmoledDarkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_DARK, enabled).apply()
        _amoledDark.value = enabled
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_SERVER_PASSWORD = "server_password"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_THEME_MODE = "theme_mode" // system|light|dark
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AMOLED_DARK = "amoled_dark"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000/api/"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
