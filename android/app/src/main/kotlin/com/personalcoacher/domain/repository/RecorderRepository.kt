package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.RecordingSession
import com.personalcoacher.domain.model.RecordingSessionStatus
import com.personalcoacher.domain.model.Transcription
import com.personalcoacher.domain.model.TranscriptionStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RecorderRepository {
    // Recording Sessions
    fun getSessionsByUser(userId: String): Flow<List<RecordingSession>>
    suspend fun getSessionById(id: String): RecordingSession?
    fun getSessionByIdFlow(id: String): Flow<RecordingSession?>
    fun getSessionWithTranscriptions(sessionId: String): Flow<RecordingSession?>
    suspend fun createSession(userId: String, chunkDuration: Int, title: String? = null): RecordingSession
    suspend fun updateSessionStatus(sessionId: String, status: RecordingSessionStatus)
    suspend fun updateSessionTitle(sessionId: String, title: String)
    suspend fun endSession(sessionId: String, status: RecordingSessionStatus = RecordingSessionStatus.COMPLETED)
    suspend fun deleteSession(sessionId: String)

    // Transcriptions
    fun getTranscriptionsBySession(sessionId: String): Flow<List<Transcription>>
    suspend fun getTranscriptionById(id: String): Transcription?
    suspend fun createTranscription(
        sessionId: String,
        chunkIndex: Int,
        startTime: Instant,
        endTime: Instant,
        duration: Int,
        audioFilePath: String? = null
    ): Transcription
    suspend fun updateTranscriptionStatus(transcriptionId: String, status: TranscriptionStatus)
    suspend fun updateTranscriptionContent(transcriptionId: String, content: String)
    suspend fun updateTranscriptionError(transcriptionId: String, errorMessage: String)
    suspend fun deleteTranscription(transcriptionId: String)
    suspend fun resetTranscriptionForRetry(transcriptionId: String)
    suspend fun clearAudioFilePath(transcriptionId: String)
}
