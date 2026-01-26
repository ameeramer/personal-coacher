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
    val currentConversationId: String? = null,  // Track conversation ID separately
    // Debug mode state
    val debugMode: Boolean = false,
    val isLoadingDebug: Boolean = false,
    val debugLogs: String = "",
    val debugSystemPrompt: String = "",
    val debugSummary: String = "",
    val showDebugPanel: Boolean = false
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
                val messages = conversation?.messages ?: emptyList()

                // Check if there's a pending assistant message (BackgroundChatWorker is processing)
                val hasPendingAssistantMessage = messages.any {
                    it.role == com.personalcoacher.domain.model.MessageRole.ASSISTANT &&
                    it.status == MessageStatus.PENDING
                }

                // Filter out pending empty assistant messages from UI (show typing indicator instead)
                val displayMessages = messages.filter { msg ->
                    !(msg.role == com.personalcoacher.domain.model.MessageRole.ASSISTANT &&
                      msg.status == MessageStatus.PENDING &&
                      msg.content.isBlank())
                }

                _uiState.update {
                    it.copy(
                        currentConversation = conversation,
                        messages = displayMessages,
                        isLoading = false,
                        // Show typing indicator if there's a pending assistant message
                        isTyping = hasPendingAssistantMessage
                    )
                }

                // Mark all completed assistant messages as "seen"
                messages
                    .filter { it.role == com.personalcoacher.domain.model.MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED }
                    .forEach { msg ->
                        chatRepository.markMessageAsSeen(msg.id)
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

            // Use background worker ONLY - no direct API call
            // This avoids the race condition where both direct call and worker complete
            // The BackgroundChatWorker uses WorkManager which CAN access network in background on Android 15+
            val result = chatRepository.sendMessageBackground(
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
                            // Keep isTyping = true until we get the response from BackgroundChatWorker
                            currentConversationId = sendResult.conversationId,
                            showConversationList = false
                        )
                    }

                    // Start watching the conversation for updates from BackgroundChatWorker
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

    /**
     * Toggle debug mode on/off
     */
    fun toggleDebugMode() {
        _uiState.update { it.copy(debugMode = !it.debugMode) }
    }

    /**
     * Toggle the debug panel visibility
     */
    fun toggleDebugPanel() {
        _uiState.update { it.copy(showDebugPanel = !it.showDebugPanel) }
    }

    /**
     * Clear debug logs
     */
    fun clearDebugLogs() {
        _uiState.update {
            it.copy(
                debugLogs = "",
                debugSystemPrompt = "",
                debugSummary = "",
                showDebugPanel = false
            )
        }
    }

    /**
     * Send a message in debug mode - captures all RAG pipeline logs
     * and displays them instead of sending to the LLM
     */
    fun sendDebugMessage() {
        val message = _uiState.value.messageInput.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            val userId = currentUserId ?: return@launch

            _uiState.update {
                it.copy(
                    isLoadingDebug = true,
                    messageInput = "",
                    debugLogs = "",
                    debugSystemPrompt = "",
                    debugSummary = ""
                )
            }

            when (val result = chatRepository.getDebugRagContext(userId, message)) {
                is Resource.Success -> {
                    result.data?.let { debugResult ->
                        _uiState.update {
                            it.copy(
                                isLoadingDebug = false,
                                debugLogs = debugResult.debugLogs,
                                debugSystemPrompt = debugResult.systemPrompt,
                                debugSummary = debugResult.summaryStats,
                                showDebugPanel = true
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDebug = false,
                            error = result.message,
                            debugLogs = "Error: ${result.message}",
                            showDebugPanel = true
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversationJob?.cancel()
    }
}
