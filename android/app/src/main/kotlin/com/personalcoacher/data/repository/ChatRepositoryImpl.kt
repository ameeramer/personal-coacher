package com.personalcoacher.data.repository

import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.entity.ConversationEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.CreateConversationRequest
import com.personalcoacher.data.remote.dto.SendMessageRequest
import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageRole
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.SendMessageResult
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun getConversations(userId: String): Flow<List<ConversationWithLastMessage>> {
        return conversationDao.getConversationsForUser(userId)
            .map { conversations ->
                conversations.map { conv ->
                    val lastMessage = messageDao.getLastMessageForConversation(conv.id)
                        .firstOrNull()?.toDomainModel()
                    ConversationWithLastMessage(
                        conversation = conv.toDomainModel(),
                        lastMessage = lastMessage
                    )
                }
            }
    }

    override fun getConversation(id: String): Flow<Conversation?> {
        return combine(
            conversationDao.getConversationById(id),
            messageDao.getMessagesForConversation(id)
        ) { conv, messages ->
            conv?.toDomainModel(messages)
        }
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId)
            .map { messages -> messages.map { it.toDomainModel() } }
    }

    override suspend fun createConversation(userId: String, title: String?): Resource<Conversation> {
        val now = Instant.now()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        conversationDao.insertConversation(ConversationEntity.fromDomainModel(conversation))
        return Resource.success(conversation)
    }

    override suspend fun sendMessage(
        conversationId: String?,
        userId: String,
        message: String,
        initialAssistantMessage: String?
    ): Resource<SendMessageResult> {
        return try {
            val response = api.sendMessage(
                SendMessageRequest(
                    message = message,
                    conversationId = conversationId,
                    initialAssistantMessage = initialAssistantMessage
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val convId = result.conversationId

                // Ensure conversation exists locally
                if (conversationDao.getConversationByIdSync(convId) == null) {
                    val now = Instant.now()
                    conversationDao.insertConversation(
                        ConversationEntity(
                            id = convId,
                            userId = userId,
                            title = message.take(50) + if (message.length > 50) "..." else "",
                            createdAt = now.toEpochMilli(),
                            updatedAt = now.toEpochMilli(),
                            syncStatus = SyncStatus.SYNCED.name
                        )
                    )
                }

                // Save user message
                val userMessage = result.userMessage.toDomainModel(convId)
                messageDao.insertMessage(
                    MessageEntity.fromDomainModel(userMessage.copy(syncStatus = SyncStatus.SYNCED))
                )

                // Save pending assistant message
                val pendingMessage = Message(
                    id = result.pendingMessage.id,
                    conversationId = convId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    status = MessageStatus.PENDING,
                    createdAt = Instant.parse(result.pendingMessage.createdAt),
                    updatedAt = Instant.parse(result.pendingMessage.createdAt),
                    syncStatus = SyncStatus.SYNCED
                )
                messageDao.insertMessage(MessageEntity.fromDomainModel(pendingMessage))

                // Update conversation timestamp
                conversationDao.updateTimestamp(convId, Instant.now().toEpochMilli())

                Resource.success(
                    SendMessageResult(
                        conversationId = convId,
                        userMessage = userMessage,
                        pendingMessageId = result.pendingMessage.id
                    )
                )
            } else {
                Resource.error("Failed to send message: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.error("Failed to send message: ${e.localizedMessage ?: "Network error"}")
        }
    }

    override suspend fun checkMessageStatus(messageId: String): Resource<Message?> {
        return try {
            val response = api.getMessageStatus(messageId)
            if (response.isSuccessful && response.body()?.message != null) {
                val messageDto = response.body()!!.message!!
                val message = messageDto.toDomainModel()

                // Update local message
                messageDao.updateMessageContent(
                    id = messageId,
                    content = message.content,
                    status = message.status.toApiString(),
                    updatedAt = message.updatedAt.toEpochMilli()
                )

                Resource.success(message)
            } else {
                Resource.success(null)
            }
        } catch (e: Exception) {
            Resource.error("Failed to check status: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteConversation(id: String): Resource<Unit> {
        conversationDao.deleteConversation(id)
        messageDao.deleteMessagesForConversation(id)
        return Resource.success(Unit)
    }

    override suspend fun uploadConversations(userId: String): Resource<Unit> {
        return try {
            val localOnlyConversations = conversationDao.getConversationsBySyncStatus(SyncStatus.LOCAL_ONLY.name)

            for (conversation in localOnlyConversations) {
                try {
                    // Get all messages for this conversation
                    val messages = messageDao.getMessagesForConversationSync(conversation.id)

                    // Skip conversations with no messages (empty local placeholders)
                    if (messages.isEmpty()) {
                        // Just mark as synced since there's nothing to upload
                        conversationDao.updateSyncStatus(conversation.id, SyncStatus.SYNCED.name)
                        continue
                    }

                    // For conversations with messages, they were likely created through sendMessage API
                    // which already syncs to server. Mark as synced.
                    conversationDao.updateSyncStatus(conversation.id, SyncStatus.SYNCED.name)

                    // Mark all messages as synced
                    for (message in messages) {
                        messageDao.updateSyncStatus(message.id, SyncStatus.SYNCED.name)
                    }
                } catch (e: Exception) {
                    // Continue with other conversations
                }
            }

            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Upload failed: ${e.localizedMessage}")
        }
    }

    override suspend fun syncConversations(userId: String): Resource<Unit> {
        return try {
            val response = api.getConversations()
            if (response.isSuccessful && response.body() != null) {
                val serverConversations = response.body()!!
                serverConversations.forEach { dto ->
                    conversationDao.insertConversation(
                        ConversationEntity.fromDomainModel(
                            dto.toDomainModel().copy(syncStatus = SyncStatus.SYNCED)
                        )
                    )

                    // Fetch full conversation with messages
                    try {
                        val convResponse = api.getConversation(dto.id)
                        if (convResponse.isSuccessful && convResponse.body()?.messages != null) {
                            convResponse.body()!!.messages?.forEach { msgDto ->
                                messageDao.insertMessage(
                                    MessageEntity.fromDomainModel(
                                        msgDto.toDomainModel(dto.id).copy(syncStatus = SyncStatus.SYNCED)
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Continue with other conversations
                    }
                }
            }
            Resource.success(Unit)
        } catch (e: Exception) {
            Resource.error("Sync failed: ${e.localizedMessage}")
        }
    }
}
