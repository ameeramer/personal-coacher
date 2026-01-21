package com.personalcoacher.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.StreamingChatEvent
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachUiState(
    val conversations: List<ConversationWithLastMessage> = emptyList(),
    val currentConversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val messageInput: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showConversationList: Boolean = true,
    val pendingMessageId: String? = null,
    val streamingContent: String = "",  // Content being streamed
    val isStreaming: Boolean = false,   // Whether streaming is in progress
    val currentConversationId: String? = null,  // Track conversation ID separately for streaming
    val debugLogs: List<String> = emptyList(),  // Debug logs for "Send & Debug" mode
    val showDebugDialog: Boolean = false,  // Whether to show debug dialog
    val isDebugMode: Boolean = false,  // Whether currently running in debug mode (allows showing logs mid-stream)
    val cloudJobId: String? = null  // Cloud job ID for server-buffered streaming (for reconnection)
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var pollingJob: Job? = null
    private var streamingJob: Job? = null
    private var conversationJob: Job? = null

    companion object {
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_POLL_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            currentUserId = tokenManager.currentUserId.first()
            val userId = currentUserId ?: return@launch

            _uiState.update { it.copy(isLoading = true) }

            chatRepository.getConversations(userId).collect { conversations ->
                _uiState.update {
                    it.copy(
                        conversations = conversations,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun refresh() {
        // Offline-first: refresh only reloads from local database
        // Remote sync is done manually via Settings
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            // Small delay to show refresh indicator, then let the Flow update naturally
            kotlinx.coroutines.delay(300)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectConversation(conversationId: String) {
        // Cancel any existing conversation collection to prevent conflicts
        conversationJob?.cancel()

        conversationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showConversationList = false,
                    isLoading = true,
                    currentConversationId = conversationId
                )
            }

            chatRepository.getConversation(conversationId).collect { conversation ->
                // Don't update messages from DB if we're actively streaming
                // (the streaming content is handled via streamingContent state)
                val currentState = _uiState.value
                if (currentState.isStreaming && currentState.pendingMessageId != null) {
                    // During streaming, only update conversation metadata, not messages
                    _uiState.update {
                        it.copy(
                            currentConversation = conversation,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            currentConversation = conversation,
                            messages = conversation?.messages ?: emptyList(),
                            isLoading = false
                        )
                    }

                    // Mark all completed assistant messages as "seen" since user is viewing them
                    conversation?.messages
                        ?.filter { it.role == com.personalcoacher.domain.model.MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED }
                        ?.forEach { msg ->
                            chatRepository.markMessageAsSeen(msg.id)
                        }

                    // Check for pending messages to poll
                    // First, check if we have a cloud job ID (server-side streaming in progress)
                    val cloudJobId = currentState.cloudJobId
                    val pendingMsg = conversation?.messages?.find { it.status == MessageStatus.PENDING }

                    if (pendingMsg != null && cloudJobId != null) {
                        // Server-side streaming - poll the cloud job
                        startCloudJobPolling(cloudJobId, pendingMsg.id)
                    } else if (pendingMsg != null) {
                        // Legacy local worker - poll the message
                        startPolling(pendingMsg.id)
                    }
                }
            }
        }
    }

    fun startNewConversation() {
        _uiState.update {
            it.copy(
                showConversationList = false,
                currentConversation = null,
                messages = emptyList()
            )
        }
    }

    /**
     * Starts a new conversation with an initial message from the AI coach.
     * This is used when the user clicks on a notification to start a conversation
     * with the notification content as the first message from the coach.
     */
    fun startConversationWithCoachMessage(coachMessage: String) {
        // Immediately switch to chat view to prevent showing conversation list
        _uiState.update { it.copy(showConversationList = false, isLoading = true) }

        viewModelScope.launch {
            val userId = currentUserId ?: tokenManager.currentUserId.first() ?: return@launch
            currentUserId = userId

            // Create a new conversation with the coach's message as the first message
            val result = chatRepository.createConversationWithCoachMessage(userId, coachMessage)

            when (result) {
                is Resource.Success -> {
                    val conversationId = result.data
                    if (conversationId != null) {
                        // Navigate to the new conversation
                        selectConversation(conversationId)
                    } else {
                        // Fallback: just start a new conversation and show the message
                        _uiState.update {
                            it.copy(
                                showConversationList = false,
                                isLoading = false,
                                currentConversation = null,
                                messages = listOf(
                                    Message(
                                        id = java.util.UUID.randomUUID().toString(),
                                        conversationId = "",
                                        role = com.personalcoacher.domain.model.MessageRole.ASSISTANT,
                                        content = coachMessage,
                                        status = MessageStatus.COMPLETED,
                                        createdAt = java.time.Instant.now(),
                                        updatedAt = java.time.Instant.now(),
                                        syncStatus = com.personalcoacher.domain.model.SyncStatus.LOCAL_ONLY
                                    )
                                )
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message ?: "Failed to create conversation",
                            showConversationList = false,
                            isLoading = false,
                            currentConversation = null,
                            messages = listOf(
                                Message(
                                    id = java.util.UUID.randomUUID().toString(),
                                    conversationId = "",
                                    role = com.personalcoacher.domain.model.MessageRole.ASSISTANT,
                                    content = coachMessage,
                                    status = MessageStatus.COMPLETED,
                                    createdAt = java.time.Instant.now(),
                                    updatedAt = java.time.Instant.now(),
                                    syncStatus = com.personalcoacher.domain.model.SyncStatus.LOCAL_ONLY
                                )
                            )
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun backToConversationList() {
        stopPolling()
        stopStreaming()
        conversationJob?.cancel()
        conversationJob = null
        _uiState.update {
            it.copy(
                showConversationList = true,
                currentConversation = null,
                messages = emptyList(),
                messageInput = "",
                streamingContent = "",
                isStreaming = false,
                pendingMessageId = null,
                currentConversationId = null,
                cloudJobId = null,  // Clear cloud job ID
                isLoading = false  // Ensure loading state is cleared
            )
        }
    }

    private fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
    }

    fun updateMessageInput(input: String) {
        _uiState.update { it.copy(messageInput = input) }
    }

    fun sendMessage() {
        sendMessageInternal(debugMode = false)
    }

    fun sendMessageWithDebug() {
        sendMessageInternal(debugMode = true)
    }

    fun showDebugLogs() {
        _uiState.update { it.copy(showDebugDialog = true) }
    }

    private fun sendMessageInternal(debugMode: Boolean) {
        val message = _uiState.value.messageInput.trim()
        if (message.isEmpty()) return

        // Cancel any existing streaming job
        stopStreaming()

        // Cancel conversation collection to avoid race conditions during streaming
        conversationJob?.cancel()
        conversationJob = null

        streamingJob = viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val conversationId = _uiState.value.currentConversation?.id
                ?: _uiState.value.currentConversationId

            _uiState.update {
                it.copy(
                    isSending = true,
                    messageInput = "",
                    streamingContent = "",
                    isStreaming = true,
                    isDebugMode = debugMode,
                    debugLogs = if (debugMode) listOf("[DEBUG] Starting streaming request...") else emptyList()
                )
            }

            var newConversationId: String? = null

            // Debug callback to capture logs
            val debugCallback: ((String) -> Unit)? = if (debugMode) {
                { logMessage ->
                    _uiState.update { state ->
                        state.copy(debugLogs = state.debugLogs + logMessage)
                    }
                }
            } else null

            try {
                // Use cloud streaming which continues on server even if app goes to background
                chatRepository.sendMessageCloudStreaming(
                    conversationId = conversationId,
                    userId = userId,
                    message = message,
                    fcmToken = null,  // TODO: Add FCM token for push notifications
                    debugMode = debugMode,
                    debugCallback = debugCallback
                ).collect { event ->
                    when (event) {
                        is StreamingChatEvent.Started -> {
                            newConversationId = event.conversationId

                            // Add user message to the local messages list immediately
                            _uiState.update { currentState ->
                                val updatedMessages = currentState.messages + event.userMessage
                                currentState.copy(
                                    isSending = false,
                                    pendingMessageId = event.assistantMessageId,
                                    messages = updatedMessages,
                                    currentConversationId = event.conversationId,
                                    showConversationList = false  // Ensure we're on chat view
                                )
                            }

                            // If this was a new conversation, we need to set up the conversation flow
                            // but we do it AFTER streaming is complete to avoid race conditions
                        }
                        is StreamingChatEvent.TextDelta -> {
                            _uiState.update {
                                it.copy(streamingContent = it.streamingContent + event.text)
                            }
                        }
                        is StreamingChatEvent.Complete -> {
                            val finalContent = event.fullContent
                            val pendingId = _uiState.value.pendingMessageId

                            _uiState.update { currentState ->
                                // Update the pending message in the list with final content
                                val updatedMessages = if (pendingId != null) {
                                    currentState.messages.map { msg ->
                                        if (msg.id == pendingId) {
                                            msg.copy(
                                                content = finalContent,
                                                status = MessageStatus.COMPLETED
                                            )
                                        } else {
                                            msg
                                        }
                                    }.let { msgs ->
                                        // If pending message wasn't in the list, add it
                                        if (msgs.none { it.id == pendingId }) {
                                            msgs + Message(
                                                id = pendingId,
                                                conversationId = newConversationId ?: conversationId ?: "",
                                                role = com.personalcoacher.domain.model.MessageRole.ASSISTANT,
                                                content = finalContent,
                                                status = MessageStatus.COMPLETED,
                                                createdAt = java.time.Instant.now(),
                                                updatedAt = java.time.Instant.now(),
                                                syncStatus = com.personalcoacher.domain.model.SyncStatus.LOCAL_ONLY
                                            )
                                        } else {
                                            msgs
                                        }
                                    }
                                } else {
                                    currentState.messages
                                }

                                currentState.copy(
                                    pendingMessageId = null,
                                    cloudJobId = null,  // Clear cloud job ID on completion
                                    streamingContent = "",
                                    isStreaming = false,
                                    isDebugMode = false,
                                    messages = updatedMessages,
                                    showDebugDialog = debugMode && currentState.debugLogs.isNotEmpty(),
                                    debugLogs = currentState.debugLogs + if (debugMode) "[DEBUG] Streaming completed successfully" else ""
                                )
                            }

                            // Mark the message as "seen" since user is watching the streaming
                            pendingId?.let { messageId ->
                                chatRepository.markMessageAsSeen(messageId)
                            }

                            // Now that streaming is complete, start collecting conversation updates
                            // This will sync with any database changes
                            newConversationId?.let { convId ->
                                if (conversationId == null) {
                                    // Small delay to let the database update settle
                                    delay(100)
                                    selectConversation(convId)
                                }
                            }
                        }
                        is StreamingChatEvent.Error -> {
                            // Check if this is a connection abort (user left app)
                            // With cloud streaming, the server continues processing even when we disconnect
                            val isConnectionAbort = event.message.contains("connection abort", ignoreCase = true) ||
                                    event.message.contains("Socket closed", ignoreCase = true) ||
                                    event.message.contains("SocketException", ignoreCase = true) ||
                                    event.message.contains("UnknownHostException", ignoreCase = true) ||
                                    event.message.contains("Unable to resolve host", ignoreCase = true)

                            if (isConnectionAbort) {
                                // User left the app - the server will continue streaming
                                // and send a push notification when complete
                                val currentConvId = newConversationId ?: conversationId
                                val currentCloudJobId = _uiState.value.cloudJobId
                                val currentPendingMessageId = _uiState.value.pendingMessageId

                                _uiState.update {
                                    it.copy(
                                        isSending = false,
                                        isStreaming = false,
                                        isDebugMode = false,
                                        streamingContent = "",
                                        // Don't restore messageInput - the message WAS sent
                                        // Keep pendingMessageId AND cloudJobId so we can poll for completion when app returns
                                        // Don't show error - this is expected when leaving the app
                                        showDebugDialog = debugMode && it.debugLogs.isNotEmpty(),
                                        debugLogs = it.debugLogs + if (debugMode) "[DEBUG] Connection interrupted - server will continue streaming" else ""
                                    )
                                }

                                // If we have a cloud job ID, start polling for the buffered content
                                // This allows the server to continue streaming while the app is in background
                                if (currentCloudJobId != null && currentPendingMessageId != null) {
                                    startCloudJobPolling(currentCloudJobId, currentPendingMessageId)
                                } else {
                                    // Fallback: Start collecting conversation updates so UI refreshes when server completes
                                    currentConvId?.let { convId ->
                                        delay(100) // Small delay to let streaming cleanup
                                        selectConversation(convId)
                                    }
                                }
                            } else {
                                // Genuine API error - restore message input so user can retry
                                _uiState.update {
                                    it.copy(
                                        isSending = false,
                                        isStreaming = false,
                                        isDebugMode = false,
                                        streamingContent = "",
                                        pendingMessageId = null,
                                        error = event.message,
                                        messageInput = message, // Restore the message on error
                                        showDebugDialog = debugMode && it.debugLogs.isNotEmpty(),
                                        debugLogs = it.debugLogs + if (debugMode) "[DEBUG] Error: ${event.message}" else ""
                                    )
                                }
                            }
                        }
                        is StreamingChatEvent.DebugLog -> {
                            if (debugMode) {
                                _uiState.update {
                                    it.copy(debugLogs = it.debugLogs + event.message)
                                }
                            }
                        }
                        is StreamingChatEvent.CloudJobStarted -> {
                            // Cloud streaming job started - includes jobId for reconnection
                            newConversationId = event.conversationId

                            // Add user message to the local messages list immediately
                            _uiState.update { currentState ->
                                val updatedMessages = currentState.messages + event.userMessage
                                currentState.copy(
                                    isSending = false,
                                    pendingMessageId = event.assistantMessageId,
                                    messages = updatedMessages,
                                    currentConversationId = event.conversationId,
                                    showConversationList = false,  // Ensure we're on chat view
                                    cloudJobId = event.jobId  // Store job ID for reconnection
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle any unexpected exceptions during streaming
                _uiState.update {
                    it.copy(
                        isSending = false,
                        isStreaming = false,
                        isDebugMode = false,
                        streamingContent = "",
                        pendingMessageId = null,
                        error = e.message ?: "An unexpected error occurred",
                        messageInput = message // Restore the message on error
                    )
                }
            }
        }
    }

    private fun startPolling(messageId: String) {
        stopPolling()
        val startTime = System.currentTimeMillis()

        pollingJob = viewModelScope.launch {
            while (true) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > MAX_POLL_DURATION_MS) {
                    _uiState.update {
                        it.copy(
                            error = "Response took too long. Please try again.",
                            pendingMessageId = null
                        )
                    }
                    break
                }

                delay(POLL_INTERVAL_MS)

                when (val result = chatRepository.checkMessageStatus(messageId)) {
                    is Resource.Success -> {
                        val message = result.data
                        if (message != null && message.status == MessageStatus.COMPLETED && message.content.isNotEmpty()) {
                            _uiState.update { it.copy(pendingMessageId = null) }
                            break
                        }
                    }
                    is Resource.Error -> {
                        // Continue polling on error
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Polls the cloud job status when the app returns from background.
     * The server continues streaming while the app is backgrounded and buffers the content.
     * This method retrieves the buffered content and updates the local message.
     */
    private fun startCloudJobPolling(jobId: String, messageId: String) {
        stopPolling()
        val startTime = System.currentTimeMillis()

        pollingJob = viewModelScope.launch {
            while (true) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > MAX_POLL_DURATION_MS) {
                    _uiState.update {
                        it.copy(
                            error = "Response took too long. Please try again.",
                            pendingMessageId = null,
                            cloudJobId = null
                        )
                    }
                    break
                }

                delay(POLL_INTERVAL_MS)

                when (val result = chatRepository.getCloudChatJobStatus(jobId)) {
                    is Resource.Success -> {
                        val status = result.data
                        if (status != null) {
                            when (status.status) {
                                "COMPLETED" -> {
                                    // Update the pending message with the buffered content
                                    val bufferedContent = status.buffer
                                    _uiState.update { currentState ->
                                        val updatedMessages = currentState.messages.map { msg ->
                                            if (msg.id == messageId) {
                                                msg.copy(
                                                    content = bufferedContent,
                                                    status = MessageStatus.COMPLETED
                                                )
                                            } else {
                                                msg
                                            }
                                        }
                                        currentState.copy(
                                            messages = updatedMessages,
                                            pendingMessageId = null,
                                            cloudJobId = null
                                        )
                                    }
                                    // Mark as seen
                                    chatRepository.markMessageAsSeen(messageId)
                                    break
                                }
                                "FAILED" -> {
                                    _uiState.update {
                                        it.copy(
                                            error = status.error ?: "Cloud chat failed",
                                            pendingMessageId = null,
                                            cloudJobId = null
                                        )
                                    }
                                    break
                                }
                                "STREAMING" -> {
                                    // Still in progress - update UI with buffered content if available
                                    if (status.buffer.isNotBlank()) {
                                        _uiState.update { currentState ->
                                            currentState.copy(
                                                streamingContent = status.buffer,
                                                isStreaming = true
                                            )
                                        }
                                    }
                                    // Continue polling
                                }
                            }
                        }
                    }
                    is Resource.Error -> {
                        // Continue polling on error (might be temporary network issue)
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversation.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissDebugDialog() {
        _uiState.update { it.copy(showDebugDialog = false, debugLogs = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        stopStreaming()
        conversationJob?.cancel()
    }
}
