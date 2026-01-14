package com.personalcoacher.domain.model

import java.time.Instant

enum class TranscriptionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class Transcription(
    val id: String,
    val sessionId: String,
    val chunkIndex: Int,
    val content: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Int, // Duration in seconds
    val status: TranscriptionStatus,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
