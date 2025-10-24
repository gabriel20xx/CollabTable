package com.collabtable.app.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun toFormattedString(): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val time = dateFormat.format(Date(timestamp))
        return "[$time] [${level.name}] [$tag] $message"
    }
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

object Logger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private const val MAX_LOGS = 500
    
    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
        Log.d(tag, message)
    }
    
    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
        Log.i(tag, message)
    }
    
    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
        Log.w(tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message: ${throwable.message}" else message
        log(LogLevel.ERROR, tag, msg)
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    private fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
    }
    
    fun clear() {
        _logs.value = emptyList()
    }
}
