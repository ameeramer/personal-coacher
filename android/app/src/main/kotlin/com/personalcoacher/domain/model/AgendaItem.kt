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

/**
 * Represents notification settings for an agenda item.
 * AI determines whether notifications should be sent before/after events,
 * but users can manually configure these settings.
 */
data class EventNotification(
    val id: String,
    val agendaItemId: String,
    val userId: String,

    // Before-event notification settings
    val notifyBefore: Boolean = false,
    val minutesBefore: Int? = null,
    val beforeMessage: String? = null,
    val beforeNotificationSent: Boolean = false,
    val beforeSentAt: Instant? = null,

    // After-event notification settings
    val notifyAfter: Boolean = false,
    val minutesAfter: Int? = null,
    val afterMessage: String? = null,
    val afterNotificationSent: Boolean = false,
    val afterSentAt: Instant? = null,

    // AI analysis metadata
    val aiDetermined: Boolean = true,
    val aiReasoning: String? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

/**
 * Response from AI analysis of an agenda item for notification configuration.
 */
data class EventNotificationAnalysis(
    val shouldNotifyBefore: Boolean,
    val minutesBefore: Int?,
    val beforeMessage: String?,
    val shouldNotifyAfter: Boolean,
    val minutesAfter: Int?,
    val afterMessage: String?,
    val reasoning: String
)
