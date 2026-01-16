package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.AgendaItemDao
import com.personalcoacher.data.local.dao.EventSuggestionDao
import com.personalcoacher.data.local.entity.AgendaItemEntity
import com.personalcoacher.data.local.entity.EventSuggestionEntity
import com.personalcoacher.data.remote.EventAnalysisResult
import com.personalcoacher.data.remote.EventAnalysisService
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateAgendaItemRequest
import com.personalcoacher.data.remote.dto.UpdateAgendaItemRequest
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.domain.model.EventSuggestionStatus
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.AgendaRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgendaRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val agendaItemDao: AgendaItemDao,
    private val eventSuggestionDao: EventSuggestionDao,
    private val eventAnalysisService: EventAnalysisService
) : AgendaRepository {

    override fun getAgendaItems(userId: String): Flow<List<AgendaItem>> {
        return agendaItemDao.getItemsForUser(userId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getAgendaItemsInRange(
        userId: String,
        startTime: Instant,
        endTime: Instant
    ): Flow<List<AgendaItem>> {
        return agendaItemDao.getItemsInRange(
            userId,
            startTime.toEpochMilli(),
            endTime.toEpochMilli()
        ).map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getUpcomingAgendaItems(userId: String, limit: Int): Flow<List<AgendaItem>> {
        return agendaItemDao.getUpcomingItems(userId, Instant.now().toEpochMilli(), limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getAgendaItemById(id: String): Flow<AgendaItem?> {
        return agendaItemDao.getItemById(id)
            .map { it?.toDomainModel() }
    }

    override suspend fun createAgendaItem(
        userId: String,
        title: String,
        description: String?,
        startTime: Instant,
        endTime: Instant?,
        isAllDay: Boolean,
        location: String?,
        sourceJournalEntryId: String?
    ): Resource<AgendaItem> {
        val now = Instant.now()
        val item = AgendaItem(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = location,
            sourceJournalEntryId = sourceJournalEntryId,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        // Save locally only - no automatic server sync
        agendaItemDao.insertItem(AgendaItemEntity.fromDomainModel(item))
        return Resource.success(item)
    }

    override suspend fun updateAgendaItem(
        id: String,
        title: String,
        description: String?,
        startTime: Instant,
        endTime: Instant?,
        isAllDay: Boolean,
        location: String?
    ): Resource<AgendaItem> {
        val existingItem = agendaItemDao.getItemByIdSync(id)
            ?: return Resource.error("Agenda item not found")

        val updatedItem = existingItem.copy(
            title = title,
            description = description,
            startTime = startTime.toEpochMilli(),
            endTime = endTime?.toEpochMilli(),
            isAllDay = isAllDay,
            location = location,
            updatedAt = Instant.now().toEpochMilli(),
            syncStatus = SyncStatus.LOCAL_ONLY.name
        )

        // Save locally only - no automatic server sync
        agendaItemDao.updateItem(updatedItem)
        return Resource.success(updatedItem.toDomainModel())
    }

    override suspend fun deleteAgendaItem(id: String): Resource<Unit> {
        agendaItemDao.deleteItem(id)
        return Resource.success(Unit)
    }

    override suspend fun syncAgendaItems(userId: String): Resource<Unit> {
        return try {
            val response = api.getAgendaItems()
            if (response.isSuccessful && response.body() != null) {
                val serverItems = response.body()!!
                serverItems.forEach { dto ->
                    agendaItemDao.insertItem(
                        AgendaItemEntity.fromDomainModel(
                            dto.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                        )
                    )
                }
            }
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Download failed: ${e.localizedMessage}")
        }
    }

    override suspend fun uploadAgendaItems(userId: String): Resource<Unit> {
        return try {
            val localItems = agendaItemDao.getItemsBySyncStatus(SyncStatus.LOCAL_ONLY.name)
            var uploadedCount = 0
            var failedCount = 0

            for (item in localItems) {
                try {
                    agendaItemDao.updateSyncStatus(item.id, SyncStatus.SYNCING.name)
                    val response = api.createAgendaItem(
                        CreateAgendaItemRequest(
                            title = item.title,
                            description = item.description,
                            startTime = Instant.ofEpochMilli(item.startTime).toString(),
                            endTime = item.endTime?.let { Instant.ofEpochMilli(it).toString() },
                            isAllDay = item.isAllDay,
                            location = item.location,
                            sourceJournalEntryId = item.sourceJournalEntryId
                        )
                    )
                    if (response.isSuccessful && response.body() != null) {
                        val serverItem = response.body()!!
                        agendaItemDao.deleteItem(item.id)
                        agendaItemDao.insertItem(
                            AgendaItemEntity.fromDomainModel(
                                serverItem.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                            )
                        )
                        uploadedCount++
                    } else {
                        agendaItemDao.updateSyncStatus(item.id, SyncStatus.LOCAL_ONLY.name)
                        failedCount++
                    }
                } catch (e: Exception) {
                    agendaItemDao.updateSyncStatus(item.id, SyncStatus.LOCAL_ONLY.name)
                    failedCount++
                }
            }

            if (failedCount > 0) {
                Resource.error("Uploaded $uploadedCount items, $failedCount failed")
            } else {
                Resource.success(Unit)
            }
        } catch (e: Exception) {
            Resource.error("Backup failed: ${e.localizedMessage}")
        }
    }

    // Event Suggestion operations

    override fun getPendingEventSuggestions(userId: String): Flow<List<EventSuggestion>> {
        return eventSuggestionDao.getPendingSuggestions(userId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getPendingSuggestionCount(userId: String): Flow<Int> {
        return eventSuggestionDao.getPendingSuggestionCount(userId)
    }

    override suspend fun analyzeJournalEntryForEvents(
        userId: String,
        journalEntryId: String,
        journalContent: String
    ): Resource<List<EventSuggestion>> {
        return when (val result = eventAnalysisService.analyzeJournalEntry(journalContent)) {
            is EventAnalysisResult.Success -> {
                val suggestions = result.suggestions.map { dto ->
                    EventSuggestion(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        journalEntryId = journalEntryId,
                        title = dto.title,
                        description = dto.description,
                        suggestedStartTime = Instant.parse(dto.startTime),
                        suggestedEndTime = dto.endTime?.let { Instant.parse(it) },
                        isAllDay = dto.isAllDay,
                        location = dto.location,
                        status = EventSuggestionStatus.PENDING,
                        createdAt = Instant.now(),
                        processedAt = null
                    )
                }

                // Save suggestions locally
                suggestions.forEach { suggestion ->
                    eventSuggestionDao.insertSuggestion(
                        EventSuggestionEntity.fromDomainModel(suggestion)
                    )
                }

                Resource.success(suggestions)
            }
            is EventAnalysisResult.Error -> {
                Resource.error(result.message)
            }
        }
    }

    override suspend fun acceptEventSuggestion(suggestionId: String): Resource<AgendaItem> {
        val suggestion = eventSuggestionDao.getSuggestionByIdSync(suggestionId)
            ?: return Resource.error("Suggestion not found")

        // Create agenda item from suggestion
        val result = createAgendaItem(
            userId = suggestion.userId,
            title = suggestion.title,
            description = suggestion.description,
            startTime = Instant.ofEpochMilli(suggestion.suggestedStartTime),
            endTime = suggestion.suggestedEndTime?.let { Instant.ofEpochMilli(it) },
            isAllDay = suggestion.isAllDay,
            location = suggestion.location,
            sourceJournalEntryId = suggestion.journalEntryId
        )

        if (result.isSuccess()) {
            // Update suggestion status
            eventSuggestionDao.updateSuggestionStatus(
                suggestionId,
                EventSuggestionStatus.ACCEPTED.name,
                Instant.now().toEpochMilli()
            )
        }

        return result
    }

    override suspend fun rejectEventSuggestion(suggestionId: String): Resource<Unit> {
        eventSuggestionDao.updateSuggestionStatus(
            suggestionId,
            EventSuggestionStatus.REJECTED.name,
            Instant.now().toEpochMilli()
        )
        return Resource.success(Unit)
    }

    override suspend fun clearProcessedSuggestions(userId: String): Resource<Unit> {
        eventSuggestionDao.deleteProcessedSuggestions(userId)
        return Resource.success(Unit)
    }
}
