package com.personalcoacher.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe log buffer for collecting Daily Tool generation logs.
 * Logs are displayed in the in-app debug dialog for easy debugging without Logcat.
 */
object DailyToolLogBuffer {
    private val logs = CopyOnWriteArrayList<String>()
    private const val MAX_LOG_ENTRIES = 500
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /**
     * Add a log entry with automatic timestamp.
     */
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        logs.add("[$timestamp] $message")

        // Trim old entries if buffer is too large
        while (logs.size > MAX_LOG_ENTRIES) {
            logs.removeAt(0)
        }
    }

    /**
     * Add a log entry without timestamp (for entries that already have one).
     */
    fun logRaw(message: String) {
        logs.add(message)

        // Trim old entries if buffer is too large
        while (logs.size > MAX_LOG_ENTRIES) {
            logs.removeAt(0)
        }
    }

    /**
     * Get all logs as a single string.
     */
    fun getLogs(): String {
        return if (logs.isEmpty()) {
            "No logs yet. Click 'Generate' to start."
        } else {
            logs.joinToString("\n")
        }
    }

    /**
     * Clear all logs.
     */
    fun clear() {
        logs.clear()
    }

    /**
     * Get the number of log entries.
     */
    fun size(): Int = logs.size
}
