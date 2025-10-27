package com.collabtable.app.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    fun toFormattedString(): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val time = dateFormat.format(Date(timestamp))
        return "[$time] [${level.name}] [$tag] $message"
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

object Logger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 500
    private const val DEDUPE_WINDOW_MS = 1_000L

    fun d(
        tag: String,
        message: String,
    ) {
        if (log(LogLevel.DEBUG, tag, message)) {
            Log.d(tag, message)
        }
    }

    fun i(
        tag: String,
        message: String,
    ) {
        if (log(LogLevel.INFO, tag, message)) {
            Log.i(tag, message)
        }
    }

    fun w(
        tag: String,
        message: String,
    ) {
        if (log(LogLevel.WARN, tag, message)) {
            Log.w(tag, message)
        }
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val msg = if (throwable != null) "$message: ${throwable.message}" else message
        if (log(LogLevel.ERROR, tag, msg)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val last = _logs.value.lastOrNull()
        // Suppress exact duplicate consecutive logs within a short window
        if (last != null && last.level == level && last.tag == tag && last.message == message && (now - last.timestamp) <= DEDUPE_WINDOW_MS) {
            return false
        }
        val entry = LogEntry(timestamp = now, level = level, tag = tag, message = message)
        _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
        return true
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
