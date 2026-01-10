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
    val isStreaming: Boolean = false    // Whether streaming is in progress
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
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            _uiState.update { it.copy(isRefreshing = true) }

            when (val result = chatRepository.syncConversations(userId)) {
                is Resource.Error -> {
                    _uiState.update { it.copy(isRefreshing = false, error = result.message) }
                }
                else -> {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(showConversationList = false, isLoading = true) }

            chatRepository.getConversation(conversationId).collect { conversation ->
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
        _uiState.update {
            it.copy(
                showConversationList = true,
                currentConversation = null,
                messages = emptyList(),
                messageInput = ""
            )
        }
    }

    fun updateMessageInput(input: String) {
        _uiState.update { it.copy(messageInput = input) }
    }

    fun sendMessage() {
        val message = _uiState.value.messageInput.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val conversationId = _uiState.value.currentConversation?.id

            _uiState.update {
                it.copy(
                    isSending = true,
                    messageInput = "",
                    streamingContent = "",
                    isStreaming = true
                )
            }

            chatRepository.sendMessageStreaming(conversationId, userId, message).collect { event ->
                when (event) {
                    is StreamingChatEvent.Started -> {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                pendingMessageId = event.assistantMessageId
                            )
                        }

                        // If this was a new conversation, select it
                        if (conversationId == null) {
                            selectConversation(event.conversationId)
                        }
                    }
                    is StreamingChatEvent.TextDelta -> {
                        _uiState.update {
                            it.copy(streamingContent = it.streamingContent + event.text)
                        }
                    }
                    is StreamingChatEvent.Complete -> {
                        _uiState.update {
                            it.copy(
                                pendingMessageId = null,
                                streamingContent = "",
                                isStreaming = false
                            )
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
    }
}
