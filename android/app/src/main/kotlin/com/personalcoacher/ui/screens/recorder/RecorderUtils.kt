package com.personalcoacher.ui.screens.recorder

/**
 * Formats a duration in seconds to a human-readable time string.
 * Format: HH:MM:SS for durations >= 1 hour, MM:SS otherwise.
 */
fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
