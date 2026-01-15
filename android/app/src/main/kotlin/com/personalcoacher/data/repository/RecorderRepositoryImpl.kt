package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.RecordingSessionDao
import com.personalcoacher.data.local.dao.TranscriptionDao
import com.personalcoacher.data.local.entity.RecordingSessionEntity
import com.personalcoacher.data.local.entity.TranscriptionEntity
import com.personalcoacher.domain.model.RecordingSession
import com.personalcoacher.domain.model.RecordingSessionStatus
import com.personalcoacher.domain.model.Transcription
import com.personalcoacher.domain.model.TranscriptionStatus
import com.personalcoacher.domain.repository.RecorderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RecorderRepositoryImpl @Inject constructor(
    private val recordingSessionDao: RecordingSessionDao,
    private val transcriptionDao: TranscriptionDao
) : RecorderRepository {

    override fun getSessionsByUser(userId: String): Flow<List<RecordingSession>> {
        return recordingSessionDao.getSessionsByUser(userId).map { sessions ->
            sessions.map { it.toDomainModel() }
        }
    }

    override suspend fun getSessionById(id: String): RecordingSession? {
        return recordingSessionDao.getSessionById(id)?.toDomainModel()
    }

    override fun getSessionByIdFlow(id: String): Flow<RecordingSession?> {
        return recordingSessionDao.getSessionByIdFlow(id).map { it?.toDomainModel() }
    }

    override fun getSessionWithTranscriptions(sessionId: String): Flow<RecordingSession?> {
        return combine(
            recordingSessionDao.getSessionByIdFlow(sessionId),
            transcriptionDao.getTranscriptionsBySession(sessionId)
        ) { session, transcriptions ->
            session?.toDomainModel()?.copy(
                transcriptions = transcriptions.map { it.toDomainModel() }
            )
        }
    }

    override suspend fun createSession(userId: String, chunkDuration: Int, title: String?): RecordingSession {
        val now = Instant.now()
        val session = RecordingSession(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            chunkDuration = chunkDuration,
            status = RecordingSessionStatus.RECORDING,
            startedAt = now,
            endedAt = null,
            createdAt = now,
            updatedAt = now
        )
        recordingSessionDao.insert(RecordingSessionEntity.fromDomainModel(session))
        return session
    }

    override suspend fun updateSessionStatus(sessionId: String, status: RecordingSessionStatus) {
        recordingSessionDao.updateStatus(
            id = sessionId,
            status = status.name.lowercase(),
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    override suspend fun updateSessionTitle(sessionId: String, title: String) {
        recordingSessionDao.updateTitle(
            id = sessionId,
            title = title,
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    override suspend fun endSession(sessionId: String, status: RecordingSessionStatus) {
        val now = Instant.now()
        recordingSessionDao.endSession(
            id = sessionId,
            endedAt = now.toEpochMilli(),
            status = status.name.lowercase(),
            updatedAt = now.toEpochMilli()
        )
    }

    override suspend fun deleteSession(sessionId: String) {
        recordingSessionDao.delete(sessionId)
    }

    override fun getTranscriptionsBySession(sessionId: String): Flow<List<Transcription>> {
        return transcriptionDao.getTranscriptionsBySession(sessionId).map { transcriptions ->
            transcriptions.map { it.toDomainModel() }
        }
    }

    override suspend fun getTranscriptionById(id: String): Transcription? {
        return transcriptionDao.getTranscriptionById(id)?.toDomainModel()
    }

    override suspend fun createTranscription(
        sessionId: String,
        chunkIndex: Int,
        startTime: Instant,
        endTime: Instant,
        duration: Int,
        audioFilePath: String?
    ): Transcription {
        val now = Instant.now()
        val transcription = Transcription(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            chunkIndex = chunkIndex,
            content = "",
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            status = TranscriptionStatus.PENDING,
            errorMessage = null,
            audioFilePath = audioFilePath,
            createdAt = now,
            updatedAt = now
        )
        transcriptionDao.insert(TranscriptionEntity.fromDomainModel(transcription))
        return transcription
    }

    override suspend fun updateTranscriptionStatus(transcriptionId: String, status: TranscriptionStatus) {
        transcriptionDao.updateStatus(
            id = transcriptionId,
            status = status.name.lowercase(),
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    override suspend fun updateTranscriptionContent(transcriptionId: String, content: String) {
        transcriptionDao.updateContent(
            id = transcriptionId,
            content = content,
            status = TranscriptionStatus.COMPLETED.name.lowercase(),
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    override suspend fun updateTranscriptionError(transcriptionId: String, errorMessage: String) {
        transcriptionDao.updateError(
            id = transcriptionId,
            status = TranscriptionStatus.FAILED.name.lowercase(),
            errorMessage = errorMessage,
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    override suspend fun deleteTranscription(transcriptionId: String) {
        transcriptionDao.delete(transcriptionId)
    }

    override suspend fun resetTranscriptionForRetry(transcriptionId: String) {
        transcriptionDao.resetForRetry(
            id = transcriptionId,
            status = TranscriptionStatus.PENDING.name.lowercase(),
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    override suspend fun clearAudioFilePath(transcriptionId: String) {
        transcriptionDao.clearAudioFilePath(
            id = transcriptionId,
            updatedAt = Instant.now().toEpochMilli()
        )
    }
}
