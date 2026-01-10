package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries WHERE userId = :userId ORDER BY date DESC LIMIT :limit")
    fun getEntriesForUser(userId: String, limit: Int = 50): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE userId = :userId ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentEntriesSync(userId: String, limit: Int = 5): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getEntriesInRange(userId: String, startDate: Long, endDate: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getEntriesInRangeSync(userId: String, startDate: Long, endDate: Long): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    fun getEntryById(id: String): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getEntryByIdSync(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE syncStatus = :status")
    suspend fun getEntriesBySyncStatus(status: String): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<JournalEntryEntity>)

    @Update
    suspend fun updateEntry(entry: JournalEntryEntity)

    @Query("UPDATE journal_entries SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("DELETE FROM journal_entries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM journal_entries WHERE userId = :userId")
    suspend fun getEntryCount(userId: String): Int
}
