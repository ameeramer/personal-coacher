package com.personalcoacher.domain.model

import java.time.Instant

data class Note(
    val id: String,
    val userId: String,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
