package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface JournalRepository {
    fun getEntries(userId: String, limit: Int = 50): Flow<List<JournalEntry>>

    fun getEntriesInRange(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): Flow<List<JournalEntry>>

    fun getEntryById(id: String): Flow<JournalEntry?>

    suspend fun createEntry(
        userId: String,
        content: String,
        mood: Mood?,
        tags: List<String>,
        date: Instant = Instant.now()
    ): Resource<JournalEntry>

    suspend fun updateEntry(
        id: String,
        content: String,
        mood: Mood?,
        tags: List<String>
    ): Resource<JournalEntry>

    suspend fun deleteEntry(id: String): Resource<Unit>

    suspend fun syncEntries(userId: String): Resource<Unit>

    suspend fun uploadEntries(userId: String): Resource<Unit>
}
