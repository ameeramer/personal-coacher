package com.personalcoacher.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.ConversationDao
import com.personalcoacher.data.local.dao.JournalEntryDao
import com.personalcoacher.data.local.dao.MessageDao
import com.personalcoacher.data.local.entity.ConversationEntity
import com.personalcoacher.data.local.entity.JournalEntryEntity
import com.personalcoacher.data.local.entity.MessageEntity
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.data.remote.ClaudeStreamingClient
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.StreamingResult
import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageRole
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.domain.model.SyncStatus
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.SendMessageResult
import com.personalcoacher.domain.repository.StreamingChatEvent
import com.personalcoacher.notification.BackgroundChatWorker
import com.personalcoacher.util.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: PersonalCoachApi,
    private val claudeApi: ClaudeApiService,
    private val claudeStreamingClient: ClaudeStreamingClient,
    private val tokenManager: TokenManager,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val journalEntryDao: JournalEntryDao
) : ChatRepository {

    companion object {
        private const val COACH_SYSTEM_PROMPT = """You are a supportive and insightful personal coach and journaling companion. Your role is to:

1. **Active Listening**: Pay close attention to what the user shares about their day, feelings, and experiences. Ask thoughtful follow-up questions.

2. **Gentle Guidance**: Offer suggestions for personal growth when appropriate, but never be preachy or overbearing. Frame advice as possibilities rather than directives.

3. **Emotional Support**: Validate the user's feelings and experiences. Be empathetic and understanding without being dismissive or overly positive.

4. **Pattern Recognition**: Notice recurring themes, challenges, or successes in the user's entries. Gently point these out when relevant.

5. **Goal Support**: Help the user reflect on their goals and progress. Celebrate wins, no matter how small.

6. **Journaling Encouragement**: If the user hasn't journaled recently, gently encourage them to do so. Ask about their day in an inviting way.

Communication Style:
- Be warm but not saccharine
- Be concise - respect the user's time
- Use conversational language, not clinical terminology
- Ask one question at a time to avoid overwhelming
- Remember context from previous conversations when provided

CRITICAL - Temporal Awareness:
- The current date and time are provided at the start of each conversation
- Pay attention to when journal entries were written (shown with dates and "X days ago")
- If a user mentioned an upcoming event like "presentation tomorrow" in an entry from 3 days ago, that event has ALREADY HAPPENED
- For past events: Ask how it went, not how they're feeling about it coming up
- For future events: Only ask about anticipation if the event hasn't occurred yet
- Always be aware of the current date when discussing timelines or events

Never:
- Provide medical, legal, or financial advice
- Be judgmental about the user's choices or feelings
- Push the user to share more than they're comfortable with
- Make assumptions about the user's life or circumstances
- Say you don't know the current date (it's always provided to you)"""

        private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US)
        private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    }

    /**
     * Builds the system prompt with recent journal entries as context.
     * This allows the AI coach to reference the user's journal when providing advice.
     * Includes the current date/time and relative dates for temporal awareness.
     */
    private fun buildCoachContext(recentEntries: List<JournalEntryEntity>): String {
        val now = Instant.now()
        val today = LocalDate.now()
        val currentDateTimeStr = now.atZone(ZoneId.systemDefault()).format(fullDateFormatter)

        val contextBuilder = StringBuilder(COACH_SYSTEM_PROMPT)
        contextBuilder.appendLine()
        contextBuilder.appendLine()
        contextBuilder.appendLine("## Current Date and Time")
        contextBuilder.appendLine(currentDateTimeStr)

        if (recentEntries.isEmpty()) {
            return contextBuilder.toString()
        }

        contextBuilder.appendLine()
        contextBuilder.appendLine("## Recent Journal Entries (for context)")

        recentEntries.forEachIndexed { index, entry ->
            val entryDate = Instant.ofEpochMilli(entry.date).atZone(ZoneId.systemDefault()).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(entryDate, today).toInt()
            val dateStr = entryDate.format(dateFormatter)
            val relativeTime = when (daysAgo) {
                0 -> "today"
                1 -> "yesterday"
                else -> "$daysAgo days ago"
            }

            contextBuilder.appendLine()
            contextBuilder.appendLine("### Entry ${index + 1} ($dateStr - $relativeTime)")
            if (!entry.mood.isNullOrBlank()) {
                contextBuilder.appendLine("Mood: ${entry.mood}")
            }
            contextBuilder.appendLine("Content: ${entry.content}")
            contextBuilder.appendLine("---")
        }

        return contextBuilder.toString()
    }

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

    override suspend fun createConversationWithCoachMessage(userId: String, coachMessage: String): Resource<String> {
        return try {
            val now = Instant.now()
            val convId = UUID.randomUUID().toString()

            // Create a title from the coach message
            val title = coachMessage.take(50) + if (coachMessage.length > 50) "..." else ""

            // Create the conversation
            conversationDao.insertConversation(
                ConversationEntity(
                    id = convId,
                    userId = userId,
                    title = title,
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                    syncStatus = SyncStatus.LOCAL_ONLY.name
                )
            )

            // Create the coach (assistant) message as the first message in the conversation
            val coachMessageEntity = Message(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = coachMessage,
                status = MessageStatus.COMPLETED,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            messageDao.insertMessage(MessageEntity.fromDomainModel(coachMessageEntity))

            Resource.success(convId)
        } catch (e: Exception) {
            Resource.error("Failed to create conversation: ${e.localizedMessage}")
        }
    }

    override suspend fun sendMessage(
        conversationId: String?,
        userId: String,
        message: String,
        initialAssistantMessage: String?
    ): Resource<SendMessageResult> {
        // Check for API key first
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            return Resource.error("Please configure your Claude API key in Settings to use this feature")
        }

        return try {
            val now = Instant.now()

            // Use existing conversation or create a new one locally
            val convId = conversationId ?: UUID.randomUUID().toString()

            // Ensure conversation exists locally
            if (conversationDao.getConversationByIdSync(convId) == null) {
                conversationDao.insertConversation(
                    ConversationEntity(
                        id = convId,
                        userId = userId,
                        title = message.take(50) + if (message.length > 50) "..." else "",
                        createdAt = now.toEpochMilli(),
                        updatedAt = now.toEpochMilli(),
                        syncStatus = SyncStatus.LOCAL_ONLY.name
                    )
                )
            }

            // Save user message locally
            val userMessageId = UUID.randomUUID().toString()
            val userMessage = Message(
                id = userMessageId,
                conversationId = convId,
                role = MessageRole.USER,
                content = message,
                status = MessageStatus.COMPLETED,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            messageDao.insertMessage(MessageEntity.fromDomainModel(userMessage))

            // Build conversation history for Claude API
            val existingMessages = messageDao.getMessagesForConversationSync(convId)
            val claudeMessages = existingMessages.map { msg ->
                ClaudeMessage(
                    role = msg.role.lowercase(),
                    content = msg.content
                )
            }

            // Get recent journal entries for context
            val recentEntries = journalEntryDao.getRecentEntriesSync(userId, 5)
            val systemPrompt = buildCoachContext(recentEntries)

            // Call Claude API directly
            val response = claudeApi.sendMessage(
                apiKey = apiKey,
                request = ClaudeMessageRequest(
                    system = systemPrompt,
                    messages = claudeMessages
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                val assistantContent = result.content.firstOrNull()?.text ?: ""

                // Save assistant message locally
                val assistantMessageId = UUID.randomUUID().toString()
                val responseTime = Instant.now()
                val assistantMessage = Message(
                    id = assistantMessageId,
                    conversationId = convId,
                    role = MessageRole.ASSISTANT,
                    content = assistantContent,
                    status = MessageStatus.COMPLETED,
                    createdAt = responseTime,
                    updatedAt = responseTime,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                messageDao.insertMessage(MessageEntity.fromDomainModel(assistantMessage))

                // Update conversation timestamp
                conversationDao.updateTimestamp(convId, Instant.now().toEpochMilli())

                Resource.success(
                    SendMessageResult(
                        conversationId = convId,
                        userMessage = userMessage,
                        pendingMessageId = assistantMessageId
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = if (errorBody?.contains("invalid_api_key") == true) {
                    "Invalid API key. Please check your Claude API key in Settings."
                } else {
                    "Failed to send message: ${response.message()}"
                }
                Resource.error(errorMessage)
            }
        } catch (e: Exception) {
            Resource.error("Failed to send message: ${e.localizedMessage ?: "Network error"}")
        }
    }

    override suspend fun checkMessageStatus(messageId: String): Resource<Message?> {
        // Offline-first: only check local database, no remote API call
        return try {
            val messageEntity = messageDao.getMessageByIdSync(messageId)
            Resource.success(messageEntity?.toDomainModel())
        } catch (e: Exception) {
            Resource.error("Failed to check status: ${e.localizedMessage}")
        }
    }

    override suspend fun markMessageAsSeen(messageId: String) {
        messageDao.updateNotificationSent(messageId, true)
    }

    override suspend fun sendMessageBackground(
        conversationId: String?,
        userId: String,
        message: String
    ): Resource<SendMessageResult> {
        // Check for API key first
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            return Resource.error("Please configure your Claude API key in Settings to use this feature")
        }

        return try {
            val now = Instant.now()

            // Use existing conversation or create a new one locally
            val convId = conversationId ?: UUID.randomUUID().toString()

            // Ensure conversation exists locally
            if (conversationDao.getConversationByIdSync(convId) == null) {
                conversationDao.insertConversation(
                    ConversationEntity(
                        id = convId,
                        userId = userId,
                        title = message.take(50) + if (message.length > 50) "..." else "",
                        createdAt = now.toEpochMilli(),
                        updatedAt = now.toEpochMilli(),
                        syncStatus = SyncStatus.LOCAL_ONLY.name
                    )
                )
            }

            // Save user message locally
            val userMessageId = UUID.randomUUID().toString()
            val userMessage = Message(
                id = userMessageId,
                conversationId = convId,
                role = MessageRole.USER,
                content = message,
                status = MessageStatus.COMPLETED,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            messageDao.insertMessage(MessageEntity.fromDomainModel(userMessage, notificationSent = true))

            // Create a pending assistant message (will be filled by BackgroundChatWorker)
            val assistantMessageId = UUID.randomUUID().toString()
            val pendingAssistantMessage = Message(
                id = assistantMessageId,
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            // Set notificationSent = false so a notification will be sent if user leaves
            messageDao.insertMessage(MessageEntity.fromDomainModel(pendingAssistantMessage, notificationSent = false))

            // Update conversation timestamp
            conversationDao.updateTimestamp(convId, now.toEpochMilli())

            // Enqueue background worker to process the message
            val inputData = Data.Builder()
                .putString(BackgroundChatWorker.KEY_MESSAGE_ID, assistantMessageId)
                .putString(BackgroundChatWorker.KEY_CONVERSATION_ID, convId)
                .putString(BackgroundChatWorker.KEY_USER_ID, userId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<BackgroundChatWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${BackgroundChatWorker.WORK_NAME_PREFIX}$assistantMessageId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            Resource.success(
                SendMessageResult(
                    conversationId = convId,
                    userMessage = userMessage,
                    pendingMessageId = assistantMessageId
                )
            )
        } catch (e: Exception) {
            Resource.error("Failed to send message: ${e.localizedMessage ?: "Unknown error"}")
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

    override fun sendMessageStreaming(
        conversationId: String?,
        userId: String,
        message: String,
        debugMode: Boolean,
        debugCallback: ((String) -> Unit)?
    ): Flow<StreamingChatEvent> = flow {
        // Check for API key first
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            emit(StreamingChatEvent.Error("Please configure your Claude API key in Settings to use this feature"))
            return@flow
        }

        val now = Instant.now()

        // Use existing conversation or create a new one locally
        val convId = conversationId ?: UUID.randomUUID().toString()

        // Ensure conversation exists locally
        if (conversationDao.getConversationByIdSync(convId) == null) {
            conversationDao.insertConversation(
                ConversationEntity(
                    id = convId,
                    userId = userId,
                    title = message.take(50) + if (message.length > 50) "..." else "",
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                    syncStatus = SyncStatus.LOCAL_ONLY.name
                )
            )
        }

        // Save user message locally
        val userMessageId = UUID.randomUUID().toString()
        val userMessage = Message(
            id = userMessageId,
            conversationId = convId,
            role = MessageRole.USER,
            content = message,
            status = MessageStatus.COMPLETED,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        messageDao.insertMessage(MessageEntity.fromDomainModel(userMessage))

        // Create a placeholder assistant message
        // Set notificationSent = false so the background worker will send notification if user leaves
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantMessage = Message(
            id = assistantMessageId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        messageDao.insertMessage(MessageEntity.fromDomainModel(assistantMessage, notificationSent = false))

        // Enqueue background worker as a fallback - if user leaves the app mid-stream,
        // the worker will take over and send a notification when the response is ready
        val inputData = Data.Builder()
            .putString(BackgroundChatWorker.KEY_MESSAGE_ID, assistantMessageId)
            .putString(BackgroundChatWorker.KEY_CONVERSATION_ID, convId)
            .putString(BackgroundChatWorker.KEY_USER_ID, userId)
            .build()

        // Add network constraint - worker will only run when network is available
        // This prevents "Unable to resolve host" errors when device loses connectivity
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BackgroundChatWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(5, TimeUnit.SECONDS) // Short delay to let streaming establish connection, then fallback kicks in if user left
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS) // Retry with exponential backoff if fails
            .build()

        val workName = "${BackgroundChatWorker.WORK_NAME_PREFIX}$assistantMessageId"
        try {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            debugCallback?.invoke("[DEBUG] BackgroundChatWorker enqueued with name: $workName")
        } catch (e: Exception) {
            debugCallback?.invoke("[DEBUG] Failed to enqueue BackgroundChatWorker: ${e.message}")
        }

        // Emit started event
        emit(StreamingChatEvent.Started(convId, userMessage, assistantMessageId))

        // Build conversation history for Claude API
        val existingMessages = messageDao.getMessagesForConversationSync(convId)
            .filter { it.id != assistantMessageId } // Exclude the placeholder
        val claudeMessages = existingMessages.map { msg ->
            ClaudeMessage(
                role = msg.role.lowercase(),
                content = msg.content
            )
        }

        // Get recent journal entries for context
        val recentEntries = journalEntryDao.getRecentEntriesSync(userId, 5)
        val systemPrompt = buildCoachContext(recentEntries)

        // Call Claude API with streaming
        val request = ClaudeMessageRequest(
            system = systemPrompt,
            messages = claudeMessages,
            stream = true
        )

        val fullContent = StringBuilder()

        // Create a callback that emits debug events
        val debugEmitter: ((String) -> Unit)? = if (debugMode) {
            { logMessage ->
                // We can't emit from inside the callback, so we pass it through
                debugCallback?.invoke(logMessage)
            }
        } else null

        claudeStreamingClient.streamMessage(apiKey, request, debugMode, debugEmitter).collect { result ->
            when (result) {
                is StreamingResult.TextDelta -> {
                    fullContent.append(result.text)
                    emit(StreamingChatEvent.TextDelta(result.text))

                    // Update the assistant message content incrementally
                    messageDao.updateMessageContent(
                        id = assistantMessageId,
                        content = fullContent.toString(),
                        status = MessageStatus.PENDING.toApiString(),
                        updatedAt = Instant.now().toEpochMilli()
                    )
                }
                is StreamingResult.Complete -> {
                    // Mark message as completed
                    val finalContent = fullContent.toString()
                    messageDao.updateMessageContent(
                        id = assistantMessageId,
                        content = finalContent,
                        status = MessageStatus.COMPLETED.toApiString(),
                        updatedAt = Instant.now().toEpochMilli()
                    )

                    // Update conversation timestamp
                    conversationDao.updateTimestamp(convId, Instant.now().toEpochMilli())

                    // Streaming completed - but we don't know if user is still watching!
                    // DON'T cancel the background worker or mark notificationSent = true here.
                    // The worker will check if the user has "seen" the message (via UI interaction)
                    // and only send a notification if they haven't.
                    // The UI (CoachViewModel) is responsible for marking messages as "seen"
                    // when the user is actively viewing the conversation.

                    emit(StreamingChatEvent.Complete(finalContent))
                }
                is StreamingResult.Error -> {
                    // Check if this is a connection abort or network error (user left the app)
                    // In this case, we should NOT cancel the worker - let it handle the request
                    //
                    // On Android 15+, background network access is restricted for non-WorkManager requests.
                    // When the app goes to background, network requests will fail with UnknownHostException
                    // approximately 5 seconds after Activity.onStop(). The BackgroundChatWorker uses
                    // WorkManager which CAN access network in background, so we let it handle the request.
                    val isConnectionAbort = result.message.contains("connection abort", ignoreCase = true) ||
                            result.message.contains("Socket closed", ignoreCase = true) ||
                            result.message.contains("SocketException", ignoreCase = true) ||
                            result.message.contains("canceled", ignoreCase = true) ||
                            // Android 15+ background network restriction errors:
                            result.message.contains("UnknownHostException", ignoreCase = true) ||
                            result.message.contains("Unable to resolve host", ignoreCase = true) ||
                            result.message.contains("No address associated", ignoreCase = true)

                    if (isConnectionAbort) {
                        // User left the app mid-stream - let BackgroundChatWorker handle it
                        // Don't update the message status or cancel the worker
                        emit(StreamingChatEvent.Error(result.message))
                    } else {
                        // Genuine API error - update message and cancel worker
                        messageDao.updateMessageContent(
                            id = assistantMessageId,
                            content = fullContent.toString().ifEmpty { "Error: ${result.message}" },
                            status = MessageStatus.FAILED.toApiString(),
                            updatedAt = Instant.now().toEpochMilli()
                        )

                        // Cancel the background worker since we've handled the error in foreground
                        val workName = "${BackgroundChatWorker.WORK_NAME_PREFIX}$assistantMessageId"
                        WorkManager.getInstance(context).cancelUniqueWork(workName)
                        messageDao.updateNotificationSent(assistantMessageId, true)

                        emit(StreamingChatEvent.Error(result.message))
                    }
                }
            }
        }
    }
}
