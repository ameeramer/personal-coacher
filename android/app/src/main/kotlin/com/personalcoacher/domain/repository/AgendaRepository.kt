package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface AgendaRepository {
    // Agenda Item operations
    fun getAgendaItems(userId: String): Flow<List<AgendaItem>>

    fun getAgendaItemsInRange(
        userId: String,
        startTime: Instant,
        endTime: Instant
    ): Flow<List<AgendaItem>>

    fun getUpcomingAgendaItems(userId: String, limit: Int = 10): Flow<List<AgendaItem>>

    fun getAgendaItemById(id: String): Flow<AgendaItem?>

    suspend fun createAgendaItem(
        userId: String,
        title: String,
        description: String? = null,
        startTime: Instant,
        endTime: Instant? = null,
        isAllDay: Boolean = false,
        location: String? = null,
        sourceJournalEntryId: String? = null
    ): Resource<AgendaItem>

    suspend fun updateAgendaItem(
        id: String,
        title: String,
        description: String? = null,
        startTime: Instant,
        endTime: Instant? = null,
        isAllDay: Boolean = false,
        location: String? = null
    ): Resource<AgendaItem>

    suspend fun deleteAgendaItem(id: String): Resource<Unit>

    suspend fun syncAgendaItems(userId: String): Resource<Unit>

    suspend fun uploadAgendaItems(userId: String): Resource<Unit>

    // Event Suggestion operations
    fun getPendingEventSuggestions(userId: String): Flow<List<EventSuggestion>>

    fun getPendingSuggestionCount(userId: String): Flow<Int>

    suspend fun analyzeJournalEntryForEvents(
        userId: String,
        journalEntryId: String,
        journalContent: String
    ): Resource<List<EventSuggestion>>

    suspend fun acceptEventSuggestion(suggestionId: String): Resource<AgendaItem>

    suspend fun acceptEventSuggestionWithEdits(
        suggestionId: String,
        title: String,
        description: String? = null,
        startTime: Instant,
        endTime: Instant? = null,
        isAllDay: Boolean = false,
        location: String? = null
    ): Resource<AgendaItem>

    suspend fun rejectEventSuggestion(suggestionId: String): Resource<Unit>

    suspend fun clearProcessedSuggestions(userId: String): Resource<Unit>
}
