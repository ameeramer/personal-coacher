package com.personalcoacher.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalcoacher.data.local.entity.TranscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    fun getTranscriptionsBySession(sessionId: String): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    suspend fun getTranscriptionsBySessionSync(sessionId: String): List<TranscriptionEntity>

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    suspend fun getTranscriptionById(id: String): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    fun getTranscriptionByIdFlow(id: String): Flow<TranscriptionEntity?>

    @Query("SELECT * FROM transcriptions WHERE status = :status")
    suspend fun getTranscriptionsByStatus(status: String): List<TranscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcription: TranscriptionEntity)

    @Update
    suspend fun update(transcription: TranscriptionEntity)

    @Query("UPDATE transcriptions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE transcriptions SET content = :content, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, status: String, updatedAt: Long)

    @Query("UPDATE transcriptions SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateError(id: String, status: String, errorMessage: String, updatedAt: Long)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transcriptions WHERE sessionId = :sessionId")
    suspend fun deleteAllForSession(sessionId: String)

    @Query("UPDATE transcriptions SET status = :status, errorMessage = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun resetForRetry(id: String, status: String, updatedAt: Long)

    @Query("UPDATE transcriptions SET audioFilePath = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun clearAudioFilePath(id: String, updatedAt: Long)
}
