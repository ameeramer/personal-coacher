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
    val isTyping: Boolean = false,   // Whether the coach is "typing" (WhatsApp-style indicator)
    val currentConversationId: String? = null  // Track conversation ID separately
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var conversationJob: Job? = null

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
                if (currentState.isTyping) {
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
        conversationJob?.cancel()
        conversationJob = null
        _uiState.update {
            it.copy(
                showConversationList = true,
                currentConversation = null,
                messages = emptyList(),
                messageInput = "",
                isTyping = false,
                currentConversationId = null,
                isLoading = false
            )
        }
    }

    fun updateMessageInput(input: String) {
        _uiState.update { it.copy(messageInput = input) }
    }

    fun sendMessage() {
        val message = _uiState.value.messageInput.trim()
        if (message.isEmpty()) return

        conversationJob?.cancel()
        conversationJob = null

        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val conversationId = _uiState.value.currentConversation?.id
                ?: _uiState.value.currentConversationId

            // Add user message to UI immediately and show typing indicator
            val tempUserMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = conversationId ?: "",
                role = com.personalcoacher.domain.model.MessageRole.USER,
                content = message,
                status = MessageStatus.COMPLETED,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                syncStatus = com.personalcoacher.domain.model.SyncStatus.LOCAL_ONLY
            )

            _uiState.update {
                it.copy(
                    isSending = true,
                    messageInput = "",
                    isTyping = true,
                    messages = it.messages + tempUserMessage
                )
            }

            // Make direct API call (non-streaming) - this waits for the full response
            val result = chatRepository.sendMessage(
                conversationId = conversationId,
                userId = userId,
                message = message
            )

            when (result) {
                is Resource.Success -> {
                    val sendResult = result.data!!

                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isTyping = false,
                            currentConversationId = sendResult.conversationId,
                            showConversationList = false
                        )
                    }

                    // Refresh the conversation to get the updated messages
                    selectConversation(sendResult.conversationId)
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isTyping = false,
                            error = result.message,
                            messageInput = message // Restore the message on error
                        )
                    }
                }
                is Resource.Loading -> {}
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

    override fun onCleared() {
        super.onCleared()
        conversationJob?.cancel()
    }
}
