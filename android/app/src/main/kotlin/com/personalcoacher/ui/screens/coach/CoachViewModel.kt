package com.personalcoacher.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Conversation
import com.personalcoacher.domain.model.ConversationWithLastMessage
import com.personalcoacher.domain.model.Message
import com.personalcoacher.domain.model.MessageStatus
import com.personalcoacher.domain.repository.ChatRepository
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
    val isTyping: Boolean = false,   // Whether the coach is "typing" (WhatsApp-style indicator)
    val currentConversationId: String? = null,  // Track conversation ID separately
    val debugLogs: List<String> = emptyList(),  // Debug logs for "Send & Debug" mode
    val showDebugDialog: Boolean = false,  // Whether to show debug dialog
    val isDebugMode: Boolean = false,  // Whether currently running in debug mode
    val cloudJobId: String? = null  // Cloud job ID for polling
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
    private var conversationJob: Job? = null

    companion object {
        private const val POLL_INTERVAL_MS = 2000L  // Poll every 2 seconds
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
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            delay(300)
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
                val currentState = _uiState.value

                // Don't update messages from DB if we're showing typing indicator
                if (currentState.isTyping && currentState.pendingMessageId != null) {
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

                    // Mark all completed assistant messages as "seen"
                    conversation?.messages
                        ?.filter { it.role == com.personalcoacher.domain.model.MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED }
                        ?.forEach { msg ->
                            chatRepository.markMessageAsSeen(msg.id)
                        }

                    // Check for pending messages with a cloud job
                    val cloudJobId = currentState.cloudJobId
                    val pendingMsg = conversation?.messages?.find { it.status == MessageStatus.PENDING }

                    if (pendingMsg != null && cloudJobId != null) {
                        // Resume polling for the cloud job
                        startPollingForJobStatus(cloudJobId, pendingMsg.id)
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
     */
    fun startConversationWithCoachMessage(coachMessage: String) {
        _uiState.update { it.copy(showConversationList = false, isLoading = true) }

        viewModelScope.launch {
            val userId = currentUserId ?: tokenManager.currentUserId.first() ?: return@launch
            currentUserId = userId

            val result = chatRepository.createConversationWithCoachMessage(userId, coachMessage)

            when (result) {
                is Resource.Success -> {
                    val conversationId = result.data
                    if (conversationId != null) {
                        selectConversation(conversationId)
                    } else {
                        showFallbackCoachMessage(coachMessage)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message ?: "Failed to create conversation",
                            isLoading = false
                        )
                    }
                    showFallbackCoachMessage(coachMessage)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun showFallbackCoachMessage(coachMessage: String) {
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

    fun backToConversationList() {
        stopPolling()
        conversationJob?.cancel()
        conversationJob = null
        _uiState.update {
            it.copy(
                showConversationList = true,
                currentConversation = null,
                messages = emptyList(),
                messageInput = "",
                isTyping = false,
                pendingMessageId = null,
                currentConversationId = null,
                cloudJobId = null,
                isLoading = false
            )
        }
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

        // Cancel any existing polling
        stopPolling()
        conversationJob?.cancel()
        conversationJob = null

        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val conversationId = _uiState.value.currentConversation?.id
                ?: _uiState.value.currentConversationId

            _uiState.update {
                it.copy(
                    isSending = true,
                    messageInput = "",
                    isTyping = false,
                    isDebugMode = debugMode,
                    debugLogs = if (debugMode) listOf("[DEBUG] Starting cloud chat job...") else emptyList()
                )
            }

            // Debug callback to capture logs
            val debugCallback: ((String) -> Unit)? = if (debugMode) {
                { logMessage ->
                    _uiState.update { state ->
                        state.copy(debugLogs = state.debugLogs + logMessage)
                    }
                }
            } else null

            // Start the cloud chat job (fire-and-forget)
            val result = chatRepository.startCloudChatJob(
                conversationId = conversationId,
                userId = userId,
                message = message,
                fcmToken = null, // TODO: Add FCM token for push notifications
                debugCallback = debugCallback
            )

            when (result) {
                is Resource.Success -> {
                    val jobResult = result.data!!
                    debugCallback?.invoke("[${System.currentTimeMillis()}] Job started successfully")

                    // Update UI with user message and show typing indicator
                    _uiState.update { currentState ->
                        val updatedMessages = currentState.messages + jobResult.userMessage
                        currentState.copy(
                            isSending = false,
                            isTyping = true,
                            pendingMessageId = jobResult.assistantMessageId,
                            cloudJobId = jobResult.jobId,
                            messages = updatedMessages,
                            currentConversationId = jobResult.conversationId,
                            showConversationList = false
                        )
                    }

                    // Start polling for job completion
                    startPollingForJobStatus(jobResult.jobId, jobResult.assistantMessageId)
                }
                is Resource.Error -> {
                    debugCallback?.invoke("[${System.currentTimeMillis()}] Job failed: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isTyping = false,
                            error = result.message,
                            messageInput = message, // Restore the message on error
                            showDebugDialog = debugMode && it.debugLogs.isNotEmpty()
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Polls the cloud job status until completion (WhatsApp-style).
     */
    private fun startPollingForJobStatus(jobId: String, messageId: String) {
        stopPolling()
        val startTime = System.currentTimeMillis()
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 5

        pollingJob = viewModelScope.launch {
            // Add a small initial delay to let the server start processing
            delay(1000)

            while (true) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > MAX_POLL_DURATION_MS) {
                    _uiState.update {
                        it.copy(
                            error = "Response took too long. Please try again.",
                            isTyping = false,
                            pendingMessageId = null,
                            cloudJobId = null
                        )
                    }
                    break
                }

                val debugMode = _uiState.value.isDebugMode
                val debugCallback: ((String) -> Unit)? = if (debugMode) {
                    { logMessage ->
                        _uiState.update { state ->
                            state.copy(debugLogs = state.debugLogs + logMessage)
                        }
                    }
                } else null

                debugCallback?.invoke("[${System.currentTimeMillis()}] Polling job status...")

                when (val result = chatRepository.getCloudChatJobStatus(jobId)) {
                    is Resource.Success -> {
                        consecutiveErrors = 0
                        val status = result.data
                        if (status != null) {
                            debugCallback?.invoke("[${System.currentTimeMillis()}] Status: ${status.status}")

                            when (status.status) {
                                "COMPLETED" -> {
                                    debugCallback?.invoke("[${System.currentTimeMillis()}] ✓ Job completed!")
                                    val responseContent = status.buffer

                                    // Update the message with the response
                                    _uiState.update { currentState ->
                                        val updatedMessages = currentState.messages.map { msg ->
                                            if (msg.id == messageId) {
                                                msg.copy(
                                                    content = responseContent,
                                                    status = MessageStatus.COMPLETED
                                                )
                                            } else {
                                                msg
                                            }
                                        }.let { msgs ->
                                            // If the pending message wasn't in the list, add it
                                            if (msgs.none { it.id == messageId }) {
                                                msgs + Message(
                                                    id = messageId,
                                                    conversationId = currentState.currentConversationId ?: "",
                                                    role = com.personalcoacher.domain.model.MessageRole.ASSISTANT,
                                                    content = responseContent,
                                                    status = MessageStatus.COMPLETED,
                                                    createdAt = java.time.Instant.now(),
                                                    updatedAt = java.time.Instant.now(),
                                                    syncStatus = com.personalcoacher.domain.model.SyncStatus.LOCAL_ONLY
                                                )
                                            } else {
                                                msgs
                                            }
                                        }

                                        currentState.copy(
                                            messages = updatedMessages,
                                            isTyping = false,
                                            pendingMessageId = null,
                                            cloudJobId = null,
                                            isDebugMode = false,
                                            showDebugDialog = debugMode && currentState.debugLogs.isNotEmpty(),
                                            debugLogs = currentState.debugLogs + if (debugMode) "[DEBUG] Response received successfully" else ""
                                        )
                                    }

                                    // Mark as seen
                                    chatRepository.markMessageAsSeen(messageId)

                                    // Refresh the conversation to sync with DB
                                    _uiState.value.currentConversationId?.let { convId ->
                                        delay(100)
                                        selectConversation(convId)
                                    }
                                    break
                                }
                                "FAILED" -> {
                                    debugCallback?.invoke("[${System.currentTimeMillis()}] ✗ Job failed: ${status.error}")
                                    _uiState.update {
                                        it.copy(
                                            error = status.error ?: "Coach response failed",
                                            isTyping = false,
                                            pendingMessageId = null,
                                            cloudJobId = null,
                                            isDebugMode = false,
                                            showDebugDialog = debugMode && it.debugLogs.isNotEmpty()
                                        )
                                    }
                                    break
                                }
                                "PENDING", "STREAMING" -> {
                                    // Still processing - continue polling
                                    debugCallback?.invoke("[${System.currentTimeMillis()}] Still processing...")
                                }
                            }
                        }
                    }
                    is Resource.Error -> {
                        consecutiveErrors++
                        debugCallback?.invoke("[${System.currentTimeMillis()}] Poll error ($consecutiveErrors/$maxConsecutiveErrors): ${result.message}")

                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            _uiState.update {
                                it.copy(
                                    error = "Network connection lost. The response may still be processing - please check back later.",
                                    isTyping = false,
                                    pendingMessageId = null,
                                    cloudJobId = null,
                                    isDebugMode = false,
                                    showDebugDialog = debugMode && it.debugLogs.isNotEmpty()
                                )
                            }
                            break
                        }
                        // On network errors, wait a bit longer before retrying
                        delay(POLL_INTERVAL_MS)
                    }
                    is Resource.Loading -> {}
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
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
        conversationJob?.cancel()
    }
}
