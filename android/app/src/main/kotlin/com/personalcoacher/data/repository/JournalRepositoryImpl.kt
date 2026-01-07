package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateJournalEntryRequest
import com.personalcoacher.data.remote.dto.UpdateJournalEntryRequest
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val journalEntryDao: JournalEntryDao
) : JournalRepository {

    override fun getEntries(userId: String, limit: Int): Flow<List<JournalEntry>> {
        return journalEntryDao.getEntriesForUser(userId, limit)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getEntriesInRange(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): Flow<List<JournalEntry>> {
        return journalEntryDao.getEntriesInRange(
            userId,
            startDate.toEpochMilli(),
            endDate.toEpochMilli()
        ).map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getEntryById(id: String): Flow<JournalEntry?> {
        return journalEntryDao.getEntryById(id)
            .map { it?.toDomainModel() }
    }

    override suspend fun createEntry(
        userId: String,
        content: String,
        mood: Mood?,
        tags: List<String>,
        date: Instant
    ): Resource<JournalEntry> {
        val now = Instant.now()
        val entry = JournalEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            content = content,
            mood = mood,
            tags = tags,
            date = date,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        // Save locally first
        journalEntryDao.insertEntry(JournalEntryEntity.fromDomainModel(entry))

        // Try to sync with server
        return try {
            val response = api.createJournalEntry(
                CreateJournalEntryRequest(
                    content = content,
                    mood = mood?.name,
                    tags = tags,
                    date = date.toString()
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val serverEntry = response.body()!!.toDomainModel()
                // Update local entry with server ID and mark as synced
                journalEntryDao.deleteEntry(entry.id)
                journalEntryDao.insertEntry(
                    JournalEntryEntity.fromDomainModel(serverEntry.copy(syncStatus = SyncStatus.SYNCED))
                )
                Resource.success(serverEntry)
            } else {
                // Keep local entry, return it
                Resource.success(entry)
            }
        } catch (e: Exception) {
            // Keep local entry, return it
            Resource.success(entry)
        }
    }

    override suspend fun updateEntry(
        id: String,
        content: String,
        mood: Mood?,
        tags: List<String>
    ): Resource<JournalEntry> {
        val existingEntry = journalEntryDao.getEntryByIdSync(id)
            ?: return Resource.error("Entry not found")

        val updatedEntry = existingEntry.copy(
            content = content,
            mood = mood?.name,
            tags = tags.joinToString(","),
            updatedAt = Instant.now().toEpochMilli(),
            syncStatus = SyncStatus.LOCAL_ONLY.name
        )

        journalEntryDao.updateEntry(updatedEntry)

        return try {
            val response = api.updateJournalEntry(
                id,
                UpdateJournalEntryRequest(content, mood?.name, tags)
            )

            if (response.isSuccessful && response.body() != null) {
                val serverEntry = response.body()!!.toDomainModel()
                journalEntryDao.insertEntry(
                    JournalEntryEntity.fromDomainModel(serverEntry.copy(syncStatus = SyncStatus.SYNCED))
                )
                Resource.success(serverEntry)
            } else {
                Resource.success(updatedEntry.toDomainModel())
            }
        } catch (e: Exception) {
            Resource.success(updatedEntry.toDomainModel())
        }
    }

    override suspend fun deleteEntry(id: String): Resource<Unit> {
        journalEntryDao.deleteEntry(id)

        return try {
            val response = api.deleteJournalEntry(id)
            if (response.isSuccessful) {
                Resource.success(Unit)
            } else {
                Resource.success(Unit) // Already deleted locally
            }
        } catch (e: Exception) {
            Resource.success(Unit) // Already deleted locally
        }
    }

    override suspend fun syncEntries(userId: String): Resource<Unit> {
        return try {
            // Upload local-only entries
            val localEntries = journalEntryDao.getEntriesBySyncStatus(SyncStatus.LOCAL_ONLY.name)
            for (entry in localEntries) {
                try {
                    journalEntryDao.updateSyncStatus(entry.id, SyncStatus.SYNCING.name)
                    val response = api.createJournalEntry(
                        CreateJournalEntryRequest(
                            content = entry.content,
                            mood = entry.mood,
                            tags = if (entry.tags.isBlank()) emptyList() else entry.tags.split(","),
                            date = Instant.ofEpochMilli(entry.date).toString()
                        )
                    )
                    if (response.isSuccessful && response.body() != null) {
                        val serverEntry = response.body()!!
                        journalEntryDao.deleteEntry(entry.id)
                        journalEntryDao.insertEntry(
                            JournalEntryEntity.fromDomainModel(
                                serverEntry.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                            )
                        )
                    }
                } catch (e: Exception) {
                    journalEntryDao.updateSyncStatus(entry.id, SyncStatus.LOCAL_ONLY.name)
                }
            }

            // Download entries from server
            val response = api.getJournalEntries()
            if (response.isSuccessful && response.body() != null) {
                val serverEntries = response.body()!!
                serverEntries.forEach { dto ->
                    journalEntryDao.insertEntry(
                        JournalEntryEntity.fromDomainModel(
                            dto.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                        )
                    )
                }
            }

            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Sync failed: ${e.localizedMessage}")
        }
    }
}
