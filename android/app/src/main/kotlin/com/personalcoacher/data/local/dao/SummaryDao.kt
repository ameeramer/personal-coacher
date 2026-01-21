package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalcoacher.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    fun getSummariesForUser(userId: String, limit: Int = 20): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getSummariesForUserSync(userId: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries WHERE userId = :userId AND type = :type ORDER BY createdAt DESC LIMIT :limit")
    fun getSummariesByType(userId: String, type: String, limit: Int = 20): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE id = :id")
    fun getSummaryById(id: String): Flow<SummaryEntity?>

    @Query("SELECT * FROM summaries WHERE syncStatus = :status")
    suspend fun getSummariesBySyncStatus(status: String): List<SummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummaries(summaries: List<SummaryEntity>)

    @Query("UPDATE summaries SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun deleteSummary(id: String)

    @Query("DELETE FROM summaries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT * FROM summaries WHERE userId = :userId AND createdAt > :since ORDER BY createdAt ASC")
    suspend fun getSummariesCreatedSince(userId: String, since: Long): List<SummaryEntity>

    @Query("SELECT id FROM summaries WHERE userId = :userId")
    suspend fun getAllIdsForUser(userId: String): List<String>
}
