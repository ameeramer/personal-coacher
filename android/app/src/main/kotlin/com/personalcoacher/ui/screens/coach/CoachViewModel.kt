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
    val currentConversationId: String? = null  // Track conversation ID separately for streaming
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

                    // Check for pending messages to poll
                    conversation?.messages?.find { it.status == MessageStatus.PENDING }?.let { msg ->
                        startPolling(msg.id)
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
        val message = _uiState.value.messageInput.trim()
        if (message.isEmpty()) return

        // Cancel any existing streaming job
        stopStreaming()

        streamingJob = viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val conversationId = _uiState.value.currentConversation?.id
                ?: _uiState.value.currentConversationId

            _uiState.update {
                it.copy(
                    isSending = true,
                    messageInput = "",
                    streamingContent = "",
                    isStreaming = true
                )
            }

            var newConversationId: String? = null

            chatRepository.sendMessageStreaming(conversationId, userId, message).collect { event ->
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
                        val finalContent = event.content
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
                                streamingContent = "",
                                isStreaming = false,
                                messages = updatedMessages
                            )
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
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                isStreaming = false,
                                streamingContent = "",
                                pendingMessageId = null,
                                error = event.message,
                                messageInput = message // Restore the message on error
                            )
                        }
                    }
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

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversation.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        stopStreaming()
        conversationJob?.cancel()
    }
}
