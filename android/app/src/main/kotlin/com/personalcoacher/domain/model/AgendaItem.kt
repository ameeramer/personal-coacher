package com.personalcoacher.domain.model

import java.time.Instant

/**
 * Represents an agenda/calendar item for the user.
 */
data class AgendaItem(
    val id: String,
    val userId: String,
    val title: String,
    val description: String? = null,
    val startTime: Instant,
    val endTime: Instant? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val sourceJournalEntryId: String? = null, // Reference to the journal entry that created this item
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

/**
 * Represents an AI-detected event suggestion from a journal entry.
 * These are shown to the user for approval before being added to the agenda.
 */
data class EventSuggestion(
    val id: String,
    val userId: String,
    val journalEntryId: String,
    val title: String,
    val description: String? = null,
    val suggestedStartTime: Instant,
    val suggestedEndTime: Instant? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val status: EventSuggestionStatus = EventSuggestionStatus.PENDING,
    val createdAt: Instant,
    val processedAt: Instant? = null
)

/**
 * Status of an event suggestion.
 */
enum class EventSuggestionStatus {
    PENDING,   // Awaiting user decision
    ACCEPTED,  // User accepted, agenda item created
    REJECTED   // User rejected the suggestion
}
