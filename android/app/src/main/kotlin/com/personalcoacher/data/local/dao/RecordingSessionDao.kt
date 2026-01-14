package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingSessionDao {
    @Query("SELECT * FROM recording_sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSessionsByUser(userId: String): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): RecordingSessionEntity?

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun getSessionByIdFlow(id: String): Flow<RecordingSessionEntity?>

    @Query("SELECT * FROM recording_sessions WHERE userId = :userId AND status = :status")
    suspend fun getSessionsByStatus(userId: String, status: String): List<RecordingSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RecordingSessionEntity)

    @Update
    suspend fun update(session: RecordingSessionEntity)

    @Query("UPDATE recording_sessions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE recording_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE recording_sessions SET endedAt = :endedAt, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long, status: String, updatedAt: Long)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM recording_sessions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
