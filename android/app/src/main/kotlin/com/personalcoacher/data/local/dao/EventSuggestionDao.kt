package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.EventSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventSuggestionDao {
    @Query("SELECT * FROM event_suggestions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSuggestionsForUser(userId: String): Flow<List<EventSuggestionEntity>>

    @Query("SELECT * FROM event_suggestions WHERE userId = :userId AND status = :status ORDER BY createdAt DESC")
    fun getSuggestionsByStatus(userId: String, status: String): Flow<List<EventSuggestionEntity>>

    @Query("SELECT * FROM event_suggestions WHERE userId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingSuggestions(userId: String): Flow<List<EventSuggestionEntity>>

    @Query("SELECT * FROM event_suggestions WHERE userId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingSuggestionsSync(userId: String): List<EventSuggestionEntity>

    @Query("SELECT COUNT(*) FROM event_suggestions WHERE userId = :userId AND status = 'PENDING'")
    fun getPendingSuggestionCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM event_suggestions WHERE userId = :userId AND status = 'PENDING'")
    suspend fun getPendingSuggestionCountSync(userId: String): Int

    @Query("SELECT * FROM event_suggestions WHERE journalEntryId = :journalEntryId")
    fun getSuggestionsForJournalEntry(journalEntryId: String): Flow<List<EventSuggestionEntity>>

    @Query("SELECT * FROM event_suggestions WHERE id = :id")
    fun getSuggestionById(id: String): Flow<EventSuggestionEntity?>

    @Query("SELECT * FROM event_suggestions WHERE id = :id")
    suspend fun getSuggestionByIdSync(id: String): EventSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: EventSuggestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestions(suggestions: List<EventSuggestionEntity>)

    @Update
    suspend fun updateSuggestion(suggestion: EventSuggestionEntity)

    @Query("UPDATE event_suggestions SET status = :status, processedAt = :processedAt WHERE id = :id")
    suspend fun updateSuggestionStatus(id: String, status: String, processedAt: Long)

    @Query("DELETE FROM event_suggestions WHERE id = :id")
    suspend fun deleteSuggestion(id: String)

    @Query("DELETE FROM event_suggestions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM event_suggestions WHERE userId = :userId AND status != 'PENDING'")
    suspend fun deleteProcessedSuggestions(userId: String)
}
