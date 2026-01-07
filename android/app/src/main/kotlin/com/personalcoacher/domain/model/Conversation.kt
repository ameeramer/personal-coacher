package com.personalcoacher.domain.model

import java.time.Instant

data class Conversation(
    val id: String,
    val userId: String,
    val title: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<Message> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

data class ConversationWithLastMessage(
    val conversation: Conversation,
    val lastMessage: Message?
)
