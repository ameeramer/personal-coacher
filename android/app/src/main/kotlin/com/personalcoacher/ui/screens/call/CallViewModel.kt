package com.personalcoacher.ui.screens.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.telecom.JournalConnectionManager
import com.personalcoacher.voice.ElevenLabsTtsService
import com.personalcoacher.voice.SileroVadManager
import com.personalcoacher.voice.VoiceCallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Call screen.
 * Manages the voice call state and interactions.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val voiceCallManager: VoiceCallManager,
    private val connectionManager: JournalConnectionManager,
    private val vadManager: SileroVadManager,
    private val ttsService: ElevenLabsTtsService,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "CallViewModel"
    }

    // UI State
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    // Combined state from voice call manager
    val callState = voiceCallManager.callState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceCallManager.CallState.Idle)

    val currentTranscript = voiceCallManager.currentTranscript
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val aiResponse = voiceCallManager.aiResponse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val callDuration = voiceCallManager.callDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val currentAmplitude = vadManager.currentAmplitude
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Debug logs from VAD
    val debugLogs = vadManager.debugLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Speaker state
    val isSpeakerOn = ttsService.isSpeakerOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        // Check API keys on init
        checkApiKeys()

        // Collect call events
        viewModelScope.launch {
            voiceCallManager.callEvents.collect { event ->
                handleCallEvent(event)
            }
        }
    }

    /**
     * Checks if all required API keys are configured.
     */
    private fun checkApiKeys() {
        val hasClaudeKey = tokenManager.hasClaudeApiKey()
        val hasGeminiKey = tokenManager.hasGeminiApiKey()
        val hasElevenLabsKey = tokenManager.hasElevenLabsApiKey()

        _uiState.value = _uiState.value.copy(
            hasClaudeApiKey = hasClaudeKey,
            hasGeminiApiKey = hasGeminiKey,
            hasElevenLabsApiKey = hasElevenLabsKey,
            canStartCall = hasClaudeKey && hasGeminiKey
        )

        if (!hasClaudeKey) {
            Log.w(TAG, "Claude API key not configured")
        }
        if (!hasGeminiKey) {
            Log.w(TAG, "Gemini API key not configured")
        }
        if (!hasElevenLabsKey) {
            Log.w(TAG, "ElevenLabs API key not configured - TTS will be limited")
        }
    }

    /**
     * Handles call events from the VoiceCallManager.
     */
    private fun handleCallEvent(event: VoiceCallManager.CallEvent) {
        when (event) {
            is VoiceCallManager.CallEvent.CallStarted -> {
                Log.d(TAG, "Call started")
                _uiState.value = _uiState.value.copy(isInCall = true)
            }
            is VoiceCallManager.CallEvent.UserSpoke -> {
                Log.d(TAG, "User spoke: ${event.transcript}")
                addConversationTurn(event.transcript, isUser = true)
            }
            is VoiceCallManager.CallEvent.AiResponded -> {
                Log.d(TAG, "AI responded: ${event.response}")
                addConversationTurn(event.response, isUser = false)
            }
            is VoiceCallManager.CallEvent.CallEnded -> {
                Log.d(TAG, "Call ended, journal entry: ${event.journalEntryId}")
                _uiState.value = _uiState.value.copy(
                    isInCall = false,
                    createdJournalEntryId = event.journalEntryId
                )
            }
            is VoiceCallManager.CallEvent.Error -> {
                Log.e(TAG, "Call error: ${event.message}")
                _uiState.value = _uiState.value.copy(error = event.message)
            }
        }
    }

    /**
     * Adds a conversation turn to the UI history.
     */
    private fun addConversationTurn(text: String, isUser: Boolean) {
        val turns = _uiState.value.conversationTurns.toMutableList()
        turns.add(ConversationTurn(text, isUser))
        _uiState.value = _uiState.value.copy(conversationTurns = turns)
    }

    /**
     * Starts a voice call using the native telecom framework.
     * Falls back to direct call if telecom isn't available.
     */
    fun startCall() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                error = null,
                conversationTurns = emptyList(),
                createdJournalEntryId = null
            )

            // Try native telecom first
            if (connectionManager.hasCallPermission()) {
                connectionManager.registerPhoneAccount()
                connectionManager.startCall()
            } else {
                // Fall back to direct call (no native UI)
                Log.d(TAG, "No telecom permission, starting direct call")
                voiceCallManager.startCall()
            }
        }
    }

    /**
     * Ends the current call.
     */
    fun endCall() {
        viewModelScope.launch {
            // Try ending via connection manager first
            connectionManager.endCall()

            // If that doesn't work, end directly
            voiceCallManager.endCall()
        }
    }

    /**
     * Cancels the call without creating a journal entry.
     */
    fun cancelCall() {
        voiceCallManager.cancelCall()
    }

    /**
     * Clears any error state.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clears the created journal entry ID (after navigation).
     */
    fun clearCreatedJournalEntry() {
        _uiState.value = _uiState.value.copy(createdJournalEntryId = null)
    }

    /**
     * Toggles the speaker on/off.
     */
    fun toggleSpeaker() {
        ttsService.toggleSpeaker()
    }

    /**
     * Clears debug logs.
     */
    fun clearDebugLogs() {
        vadManager.clearDebugLogs()
    }

    /**
     * Gets all debug logs as a single string for copying.
     */
    fun getDebugLogsAsText(): String {
        return debugLogs.value.joinToString("\n")
    }
}

/**
 * UI state for the Call screen.
 */
data class CallUiState(
    val isInCall: Boolean = false,
    val hasClaudeApiKey: Boolean = false,
    val hasGeminiApiKey: Boolean = false,
    val hasElevenLabsApiKey: Boolean = false,
    val canStartCall: Boolean = false,
    val conversationTurns: List<ConversationTurn> = emptyList(),
    val createdJournalEntryId: String? = null,
    val error: String? = null
)

/**
 * A single turn in the conversation for display.
 */
data class ConversationTurn(
    val text: String,
    val isUser: Boolean
)
