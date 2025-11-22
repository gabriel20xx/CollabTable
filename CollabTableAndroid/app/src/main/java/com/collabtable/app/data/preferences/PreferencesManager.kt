package com.collabtable.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class PreferencesManager(
    context: Context,
) {
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

    private val _sortOrder = MutableStateFlow(getSortOrder())
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    // Sync polling interval in milliseconds (configurable)
    private val _syncPollIntervalMs = MutableStateFlow(getSyncPollIntervalMs())
    val syncPollIntervalMs: StateFlow<Long> = _syncPollIntervalMs.asStateFlow()

    // Notification preferences (per list event type)
    private val _notifyListAdded = MutableStateFlow(isNotifyListAddedEnabled())
    val notifyListAdded: StateFlow<Boolean> = _notifyListAdded.asStateFlow()

    private val _notifyListEdited = MutableStateFlow(isNotifyListEditedEnabled())
    val notifyListEdited: StateFlow<Boolean> = _notifyListEdited.asStateFlow()

    private val _notifyListRemoved = MutableStateFlow(isNotifyListRemovedEnabled())
    val notifyListRemoved: StateFlow<Boolean> = _notifyListRemoved.asStateFlow()

    private val _notifyListContentUpdated = MutableStateFlow(isNotifyListContentUpdatedEnabled())
    val notifyListContentUpdated: StateFlow<Boolean> = _notifyListContentUpdated.asStateFlow()

    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

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

    fun isFirstRun(): Boolean = prefs.getBoolean(KEY_FIRST_RUN, true)

    fun setIsFirstRun(isFirstRun: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_RUN, isFirstRun).apply()
    }

    fun getServerPassword(): String? = prefs.getString(KEY_SERVER_PASSWORD, null)

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
        prefs
            .edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_PASSWORD)
            .remove(KEY_LAST_SYNC_TIMESTAMP)
            .apply()
        _serverUrl.value = getServerUrl()
    }

    // Theme settings
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, THEME_MODE_SYSTEM) ?: THEME_MODE_SYSTEM

    fun setThemeMode(mode: String) {
        val normalized =
            when (mode.lowercase()) {
                THEME_MODE_LIGHT, THEME_MODE_DARK, THEME_MODE_SYSTEM -> mode.lowercase()
                else -> THEME_MODE_SYSTEM
            }
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
        _themeMode.value = normalized
    }

    fun isDynamicColorEnabled(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun isAmoledDarkEnabled(): Boolean = prefs.getBoolean(KEY_AMOLED_DARK, false)

    fun setAmoledDarkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMOLED_DARK, enabled).apply()
        _amoledDark.value = enabled
    }

    // Sorting preferences for tables overview
    fun getSortOrder(): String = prefs.getString(KEY_SORT_ORDER, SORT_UPDATED_DESC) ?: SORT_UPDATED_DESC

    fun setSortOrder(order: String) {
        val normalized =
            when (order) {
                SORT_UPDATED_DESC, SORT_UPDATED_ASC, SORT_NAME_ASC, SORT_NAME_DESC -> order
                else -> SORT_UPDATED_DESC
            }
        prefs.edit().putString(KEY_SORT_ORDER, normalized).apply()
        _sortOrder.value = normalized
    }

    // Sync interval: read/write with sensible bounds to avoid too-aggressive polling
    fun getSyncPollIntervalMs(): Long {
        val raw = prefs.getLong(KEY_SYNC_POLL_INTERVAL_MS, DEFAULT_SYNC_POLL_INTERVAL_MS)
        return raw.coerceIn(MIN_SYNC_POLL_INTERVAL_MS, MAX_SYNC_POLL_INTERVAL_MS)
    }

    fun setSyncPollIntervalMs(ms: Long) {
        val clamped = ms.coerceIn(MIN_SYNC_POLL_INTERVAL_MS, MAX_SYNC_POLL_INTERVAL_MS)
        prefs.edit().putLong(KEY_SYNC_POLL_INTERVAL_MS, clamped).apply()
        _syncPollIntervalMs.value = clamped
    }

    // Last time we checked lists for background notifications
    fun getLastListNotifyCheckTimestamp(): Long = prefs.getLong(KEY_LAST_LIST_NOTIFY_CHECK_TS, 0L)

    fun setLastListNotifyCheckTimestamp(ts: Long) {
        prefs.edit().putLong(KEY_LAST_LIST_NOTIFY_CHECK_TS, ts).apply()
    }

    // Notification settings accessors
    private fun isNotifyListAddedEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_LIST_ADDED, true)

    fun setNotifyListAddedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_LIST_ADDED, enabled).apply()
        _notifyListAdded.value = enabled
    }

    private fun isNotifyListEditedEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_LIST_EDITED, true)

    fun setNotifyListEditedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_LIST_EDITED, enabled).apply()
        _notifyListEdited.value = enabled
    }

    private fun isNotifyListRemovedEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_LIST_REMOVED, true)

    fun setNotifyListRemovedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_LIST_REMOVED, enabled).apply()
        _notifyListRemoved.value = enabled
    }

    private fun isNotifyListContentUpdatedEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_LIST_CONTENT_UPDATED, true)

    fun setNotifyListContentUpdatedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_LIST_CONTENT_UPDATED, enabled).apply()
        _notifyListContentUpdated.value = enabled
    }

    fun hasPromptedNotifications(): Boolean = prefs.getBoolean(KEY_HAS_PROMPTED_NOTIFICATIONS, false)

    fun setHasPromptedNotifications(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED_NOTIFICATIONS, prompted).apply()
    }

    // Persist per-list column widths (fieldId -> widthDp)
    // Stored as a JSON object string in SharedPreferences under key: COLUMN_WIDTHS_PREFIX + listId
    fun getColumnWidths(listId: String): Map<String, Float> {
        val key = COLUMN_WIDTHS_PREFIX + listId
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, Float>()
            val it = json.keys()
            while (it.hasNext()) {
                val fieldId = it.next()
                val width = json.optDouble(fieldId, Double.NaN)
                if (!width.isNaN()) {
                    map[fieldId] = width.toFloat()
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setColumnWidths(
        listId: String,
        widthsDp: Map<String, Float>,
    ) {
        val key = COLUMN_WIDTHS_PREFIX + listId
        val json = JSONObject()
        widthsDp.forEach { (fieldId, width) ->
            // Persist as double for precision, value is in dp units
            json.put(fieldId, width.toDouble())
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    // Persist per-list column alignments (fieldId -> alignment: "start" | "center" | "end")
    // Stored as a JSON object string under key: COLUMN_ALIGN_PREFIX + listId
    fun getColumnAlignments(listId: String): Map<String, String> {
        val key = COLUMN_ALIGN_PREFIX + listId
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val it = json.keys()
            while (it.hasNext()) {
                val fieldId = it.next()
                val align = json.optString(fieldId, "start")
                val normalized =
                    when (align.lowercase()) {
                        "center" -> "center"
                        "end", "right" -> "end"
                        else -> "start"
                    }
                map[fieldId] = normalized
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setColumnAlignments(
        listId: String,
        alignments: Map<String, String>,
    ) {
        val key = COLUMN_ALIGN_PREFIX + listId
        val json = JSONObject()
        alignments.forEach { (fieldId, alignment) ->
            val normalized =
                when (alignment.lowercase()) {
                    "center" -> "center"
                    "end", "right" -> "end"
                    else -> "start"
                }
            json.put(fieldId, normalized)
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    // Generate and cache a stable device id (UUID) for this install
    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_SERVER_PASSWORD = "server_password"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_THEME_MODE = "theme_mode" // system|light|dark
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AMOLED_DARK = "amoled_dark"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_SYNC_POLL_INTERVAL_MS = "sync_poll_interval_ms"
        private const val KEY_NOTIFY_LIST_ADDED = "notify_list_added"
        private const val KEY_NOTIFY_LIST_EDITED = "notify_list_edited"
        private const val KEY_NOTIFY_LIST_REMOVED = "notify_list_removed"
        private const val KEY_NOTIFY_LIST_CONTENT_UPDATED = "notify_list_content_updated"
        private const val KEY_LAST_LIST_NOTIFY_CHECK_TS = "last_list_notify_check_ts"
        private const val KEY_HAS_PROMPTED_NOTIFICATIONS = "has_prompted_notifications"
        private const val COLUMN_WIDTHS_PREFIX = "column_widths_" // + listId
        private const val COLUMN_ALIGN_PREFIX = "column_align_" // + listId
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000/api/"
        private const val DEFAULT_SYNC_POLL_INTERVAL_MS = 250L
        private const val MIN_SYNC_POLL_INTERVAL_MS = 250L
        private const val MAX_SYNC_POLL_INTERVAL_MS = 600_000L // 10 minutes
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"

        // Sort orders
        const val SORT_UPDATED_DESC = "updated_desc" // Newest first (default)
        const val SORT_UPDATED_ASC = "updated_asc" // Oldest first
        const val SORT_NAME_ASC = "name_asc" // A-Z
        const val SORT_NAME_DESC = "name_desc" // Z-A

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }
}
