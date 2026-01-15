package com.personalcoacher.domain.model

import java.time.Instant

enum class RecordingSessionStatus {
    RECORDING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class RecordingSession(
    val id: String,
    val userId: String,
    val title: String?,
    val chunkDuration: Int, // Duration in seconds (e.g., 1800 = 30 min)
    val status: RecordingSessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val transcriptions: List<Transcription> = emptyList()
)
