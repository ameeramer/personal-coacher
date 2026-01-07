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

enum class Mood(val emoji: String, val displayName: String) {
    HAPPY("\uD83D\uDE0A", "Happy"),
    GRATEFUL("\uD83D\uDE4F", "Grateful"),
    CALM("\uD83D\uDE0C", "Calm"),
    NEUTRAL("\uD83D\uDE10", "Neutral"),
    ANXIOUS("\uD83D\uDE1F", "Anxious"),
    SAD("\uD83D\uDE22", "Sad"),
    FRUSTRATED("\uD83D\uDE24", "Frustrated"),
    TIRED("\uD83D\uDE34", "Tired");

    companion object {
        fun fromString(value: String?): Mood? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}

enum class SyncStatus {
    LOCAL_ONLY,  // Created locally, not yet synced
    SYNCING,     // Currently being synced
    SYNCED       // Successfully synced with server
}
