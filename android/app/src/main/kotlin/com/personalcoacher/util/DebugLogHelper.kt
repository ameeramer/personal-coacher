package com.personalcoacher.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for collecting debug logs in-app.
 * Allows users to view logs without ADB access.
 */
@Singleton
class DebugLogHelper @Inject constructor() {
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val maxLogs = 200

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $tag: $message"
        logs.add(logEntry)

        // Keep only the last maxLogs entries
        while (logs.size > maxLogs) {
            logs.removeAt(0)
        }

        // Also log to Logcat for development
        android.util.Log.d(tag, message)
    }

    @Synchronized
    fun getLogs(): String {
        return if (logs.isEmpty()) {
            "No logs recorded yet.\n\nTry:\n1. Toggle notifications OFF then ON\n2. Tap 'Test Notification'\n3. Check this log again"
        } else {
            logs.joinToString("\n")
        }
    }

    @Synchronized
    fun clear() {
        logs.clear()
    }
}
