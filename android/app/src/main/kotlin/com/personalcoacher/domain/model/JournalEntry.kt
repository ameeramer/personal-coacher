package com.personalcoacher.domain.model

import java.time.Instant

data class JournalEntry(
    val id: String,
    val userId: String,
    val content: String,
    val mood: Mood?,
    val tags: List<String>,
    val date: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

enum class Mood(val emoji: String, val displayName: String, val serverValue: String) {
    GREAT("😊", "Great", "Great"),
    GOOD("🙂", "Good", "Good"),
    OKAY("😐", "Okay", "Okay"),
    STRUGGLING("😔", "Struggling", "Struggling"),
    DIFFICULT("😢", "Difficult", "Difficult");

    companion object {
        fun fromString(value: String?): Mood? {
            if (value == null) return null
            // Try to match by server value (e.g., "Great", "Good", etc.)
            return entries.find { it.serverValue.equals(value, ignoreCase = true) }
                // Fall back to matching by enum name (e.g., "GREAT", "GOOD", etc.)
                ?: entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}

enum class SyncStatus {
    LOCAL_ONLY,  // Created locally, not yet synced
    SYNCING,     // Currently being synced
    SYNCED       // Successfully synced with server
}
