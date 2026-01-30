package com.personalcoacher.voice

import android.content.Context
import android.util.Log
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.kuzu.RagEngine
import com.personalcoacher.data.remote.ClaudeMessage
import com.personalcoacher.data.remote.ClaudeMessageRequest
import com.personalcoacher.data.remote.ClaudeStreamingClient
import com.personalcoacher.data.remote.GeminiTranscriptionService
import com.personalcoacher.data.remote.StreamingResult
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.repository.JournalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the voice call experience for journaling.
 *
 * Flow:
 * 1. User starts call → AI greets with open-ended question
 * 2. VAD detects user speech → Records audio
 * 3. Silence detected → Transcribe with Gemini
 * 4. Send to Claude with RAG context → Get conversational response
 * 5. ElevenLabs TTS → Play response
 * 6. Repeat until call ends
 * 7. On call end → Transform conversation to journal entry
 */
@Singleton
class VoiceCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vadManager: SileroVadManager,
    private val transcriptionService: GeminiTranscriptionService,
    private val claudeStreamingClient: ClaudeStreamingClient,
    private val ttsService: ElevenLabsTtsService,
    private val ragEngine: RagEngine,
    private val journalRepository: JournalRepository,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "VoiceCallManager"
        private const val CLAUDE_MODEL = "claude-opus-4-5"
        private const val MAX_HISTORY_TOKENS = 4000 // Limit conversation history
    }

    // State
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: Flow<CallState> = _callState.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: Flow<String> = _currentTranscript.asStateFlow()

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: Flow<String> = _aiResponse.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: Flow<Long> = _callDuration.asStateFlow()

    // Events
    private val _callEvents = MutableSharedFlow<CallEvent>()
    val callEvents: Flow<CallEvent> = _callEvents.asSharedFlow()

    // Internal state
    private var callScope: CoroutineScope? = null
    private var callStartTime: Long = 0
    private var durationJob: Job? = null
    private val conversationHistory = mutableListOf<JournalCallPrompts.ConversationTurn>()
    private val messageHistory = mutableListOf<ClaudeMessage>()
    private var systemPrompt: String = ""
    private var userId: String? = null

    /**
     * States of the voice call
     */
    sealed class CallState {
        object Idle : CallState()
        object Connecting : CallState()
        object Listening : CallState() // Waiting for user to speak
        data class Processing(val stage: String) : CallState() // "Transcribing", "Thinking", "Generating voice"
        object Speaking : CallState() // AI is speaking
        object Ending : CallState() // Call is ending, generating journal entry
        data class Error(val message: String) : CallState()
    }

    /**
     * Events emitted during the call
     */
    sealed class CallEvent {
        object CallStarted : CallEvent()
        data class UserSpoke(val transcript: String) : CallEvent()
        data class AiResponded(val response: String) : CallEvent()
        data class CallEnded(val journalEntryId: String?) : CallEvent()
        data class Error(val message: String) : CallEvent()
    }

    /**
     * Message for Claude conversation history
     */
    private data class ClaudeMessage(
        val role: String, // "user" or "assistant"
        val content: String
    )

    /**
     * Starts a new voice call for journaling.
     */
    suspend fun startCall() {
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Call already in progress")
            return
        }

        Log.d(TAG, "Starting voice call")
        _callState.value = CallState.Connecting

        // Get user ID
        userId = tokenManager.awaitUserId()
        if (userId == null) {
            _callState.value = CallState.Error("User not logged in")
            _callEvents.emit(CallEvent.Error("User not logged in"))
            return
        }

        // Initialize scope
        callScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        callStartTime = System.currentTimeMillis()
        conversationHistory.clear()
        messageHistory.clear()
        _currentTranscript.value = ""
        _aiResponse.value = ""

        // Start duration tracker
        durationJob = callScope?.launch {
            while (true) {
                _callDuration.value = System.currentTimeMillis() - callStartTime
                kotlinx.coroutines.delay(1000)
            }
        }

        // Build context with RAG
        try {
            val ragContext = withContext(Dispatchers.IO) {
                ragEngine.retrieveContext(userId!!, "Tell me about your day")
            }
            systemPrompt = JournalCallPrompts.buildCallContext(ragContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error building RAG context", e)
            systemPrompt = JournalCallPrompts.buildCallContext(null)
        }

        // Start listening for speech events
        callScope?.launch {
            vadManager.speechEvents.collect { event ->
                handleSpeechEvent(event)
            }
        }

        // Generate initial greeting
        _callState.value = CallState.Processing("Starting")
        generateAiResponse("") // Empty user message triggers initial greeting
    }

    /**
     * Handles speech events from the VAD.
     */
    private suspend fun handleSpeechEvent(event: SileroVadManager.SpeechEvent) {
        when (event) {
            is SileroVadManager.SpeechEvent.SpeechStarted -> {
                // User started speaking - stop any ongoing TTS
                ttsService.stopSpeaking()
                _callState.value = CallState.Listening
                Log.d(TAG, "User started speaking")
            }

            is SileroVadManager.SpeechEvent.SpeechEnded -> {
                // User finished speaking - process their audio
                Log.d(TAG, "User stopped speaking, processing ${event.audioFile.name}")
                processUserSpeech(event.audioFile)
            }

            is SileroVadManager.SpeechEvent.Error -> {
                Log.e(TAG, "VAD error: ${event.message}")
                _callEvents.emit(CallEvent.Error(event.message))
            }
        }
    }

    /**
     * Processes user speech: transcribe → Claude → TTS.
     */
    private suspend fun processUserSpeech(audioFile: File) {
        val claudeApiKey = tokenManager.getClaudeApiKeySync()
        val elevenLabsApiKey = tokenManager.getElevenLabsApiKeySync()
        val geminiApiKey = tokenManager.getGeminiApiKeySync()

        if (claudeApiKey.isNullOrBlank()) {
            _callEvents.emit(CallEvent.Error("Claude API key not configured"))
            return
        }

        // Step 1: Transcribe
        _callState.value = CallState.Processing("Transcribing")
        _currentTranscript.value = "Listening..."

        val transcriptionResult = withContext(Dispatchers.IO) {
            if (geminiApiKey.isNullOrBlank()) {
                GeminiTranscriptionService.TranscriptionResult.Error("Gemini API key not configured")
            } else {
                transcriptionService.transcribeAudio(audioFile, geminiApiKey)
            }
        }

        // Clean up audio file
        audioFile.delete()

        val userText = when (transcriptionResult) {
            is GeminiTranscriptionService.TranscriptionResult.Success -> {
                transcriptionResult.text
            }
            is GeminiTranscriptionService.TranscriptionResult.Error -> {
                Log.e(TAG, "Transcription error: ${transcriptionResult.message}")
                _callEvents.emit(CallEvent.Error("Couldn't understand that. Please try again."))
                resumeListening()
                return
            }
        }

        if (userText.isBlank()) {
            Log.d(TAG, "Empty transcription, resuming listening")
            resumeListening()
            return
        }

        _currentTranscript.value = userText
        Log.d(TAG, "Transcribed: $userText")

        // Add to conversation history
        conversationHistory.add(JournalCallPrompts.ConversationTurn(userText, isUser = true))
        messageHistory.add(ClaudeMessage("user", userText))
        _callEvents.emit(CallEvent.UserSpoke(userText))

        // Step 2: Generate AI response
        generateAiResponse(userText)
    }

    /**
     * Generates AI response using Claude with streaming.
     */
    private suspend fun generateAiResponse(userText: String) {
        val claudeApiKey = tokenManager.getClaudeApiKeySync()
        val elevenLabsApiKey = tokenManager.getElevenLabsApiKeySync()

        if (claudeApiKey.isNullOrBlank()) {
            _callEvents.emit(CallEvent.Error("Claude API key not configured"))
            return
        }

        _callState.value = CallState.Processing("Thinking")
        _aiResponse.value = ""

        // Build Claude request
        val messages = messageHistory.map { msg ->
            ClaudeMessage(
                role = msg.role,
                content = msg.content
            )
        }

        val request = ClaudeMessageRequest(
            model = CLAUDE_MODEL,
            maxTokens = 300, // Keep responses short for voice
            system = systemPrompt,
            messages = messages.ifEmpty {
                // Initial greeting - no user message yet
                listOf(
                    ClaudeMessage(
                        role = "user",
                        content = "[Call just started. Give a warm, brief greeting and ask how the user's day has been.]"
                    )
                )
            }
        )

        // Stream response
        var fullResponse = StringBuilder()

        claudeStreamingClient.streamMessage(claudeApiKey, request)
            .collect { result ->
                when (result) {
                    is StreamingResult.TextDelta -> {
                        fullResponse.append(result.text)
                        _aiResponse.value = fullResponse.toString()
                    }
                    is StreamingResult.Complete -> {
                        val response = fullResponse.toString()
                        Log.d(TAG, "AI response: $response")

                        // Add to conversation history
                        conversationHistory.add(JournalCallPrompts.ConversationTurn(response, isUser = false))
                        messageHistory.add(ClaudeMessage("assistant", response))
                        _callEvents.emit(CallEvent.AiResponded(response))

                        // Speak the response
                        speakResponse(response)
                    }
                    is StreamingResult.Error -> {
                        Log.e(TAG, "Claude error: ${result.message}")
                        _callEvents.emit(CallEvent.Error("Couldn't generate response"))
                        resumeListening()
                    }
                }
            }
    }

    /**
     * Speaks the AI response using ElevenLabs TTS.
     */
    private suspend fun speakResponse(text: String) {
        val elevenLabsApiKey = tokenManager.getElevenLabsApiKeySync()

        if (elevenLabsApiKey.isNullOrBlank()) {
            Log.w(TAG, "ElevenLabs API key not configured, using fallback")
            // For now, just wait a bit and resume listening
            kotlinx.coroutines.delay(1000)
            resumeListening()
            return
        }

        _callState.value = CallState.Processing("Generating voice")

        withContext(Dispatchers.IO) {
            ttsService.speakText(
                apiKey = elevenLabsApiKey,
                text = text,
                onComplete = {
                    // Resume listening after TTS completes
                    callScope?.launch {
                        resumeListening()
                    }
                }
            )
        }

        _callState.value = CallState.Speaking
    }

    /**
     * Resumes listening for user speech.
     */
    private suspend fun resumeListening() {
        if (_callState.value is CallState.Ending || _callState.value is CallState.Idle) {
            return
        }

        _callState.value = CallState.Listening
        _currentTranscript.value = ""

        withContext(Dispatchers.IO) {
            vadManager.startListening()
        }
    }

    /**
     * Ends the call and transforms the conversation into a journal entry.
     */
    suspend fun endCall(): String? {
        if (conversationHistory.isEmpty()) {
            cleanup()
            _callEvents.emit(CallEvent.CallEnded(null))
            return null
        }

        Log.d(TAG, "Ending call, generating journal entry")
        _callState.value = CallState.Ending

        // Stop listening and speaking
        vadManager.stopListening()
        ttsService.stopSpeaking()
        durationJob?.cancel()

        val claudeApiKey = tokenManager.getClaudeApiKeySync()
        if (claudeApiKey.isNullOrBlank()) {
            cleanup()
            _callEvents.emit(CallEvent.CallEnded(null))
            return null
        }

        // Transform conversation to journal entry
        val journalEntryId = try {
            transformToJournalEntry(claudeApiKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating journal entry", e)
            null
        }

        cleanup()
        _callEvents.emit(CallEvent.CallEnded(journalEntryId))

        return journalEntryId
    }

    /**
     * Transforms the conversation into a journal entry using Claude.
     */
    private suspend fun transformToJournalEntry(claudeApiKey: String): String? = withContext(Dispatchers.IO) {
        val transcript = JournalCallPrompts.formatTranscriptForJournal(conversationHistory)

        // Generate journal entry content
        val contentRequest = ClaudeMessageRequest(
            model = CLAUDE_MODEL,
            maxTokens = 1500,
            system = JournalCallPrompts.TRANSFORM_TO_JOURNAL_PROMPT,
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = transcript
                )
            )
        )

        var journalContent = StringBuilder()
        claudeStreamingClient.streamMessage(claudeApiKey, contentRequest)
            .collect { result ->
                when (result) {
                    is StreamingResult.TextDelta -> {
                        journalContent.append(result.text)
                    }
                    else -> {}
                }
            }

        if (journalContent.isBlank()) {
            Log.e(TAG, "Empty journal content generated")
            return@withContext null
        }

        // Extract mood
        val moodRequest = ClaudeMessageRequest(
            model = CLAUDE_MODEL,
            maxTokens = 20,
            system = JournalCallPrompts.MOOD_EXTRACTION_PROMPT,
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = transcript
                )
            )
        )

        var moodString = StringBuilder()
        claudeStreamingClient.streamMessage(claudeApiKey, moodRequest)
            .collect { result ->
                when (result) {
                    is StreamingResult.TextDelta -> {
                        moodString.append(result.text)
                    }
                    else -> {}
                }
            }

        val mood = parseMood(moodString.toString().trim().uppercase())

        // Extract tags
        val tagRequest = ClaudeMessageRequest(
            model = CLAUDE_MODEL,
            maxTokens = 100,
            system = JournalCallPrompts.TAG_EXTRACTION_PROMPT,
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = transcript
                )
            )
        )

        var tagsString = StringBuilder()
        claudeStreamingClient.streamMessage(claudeApiKey, tagRequest)
            .collect { result ->
                when (result) {
                    is StreamingResult.TextDelta -> {
                        tagsString.append(result.text)
                    }
                    else -> {}
                }
            }

        val tags = tagsString.toString()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        // Create journal entry
        val result = journalRepository.createEntry(
            userId = userId!!,
            content = journalContent.toString(),
            mood = mood,
            tags = tags
        )

        val entryId = when (result) {
            is com.personalcoacher.util.Resource.Success -> {
                Log.d(TAG, "Created journal entry: ${result.data.id}")
                result.data.id
            }
            is com.personalcoacher.util.Resource.Error -> {
                Log.e(TAG, "Failed to create journal entry: ${result.message}")
                null
            }
            is com.personalcoacher.util.Resource.Loading -> null
        }

        entryId
    }

    /**
     * Parses mood string to Mood enum.
     */
    private fun parseMood(moodStr: String): Mood? {
        return when {
            moodStr.contains("GREAT") -> Mood.GREAT
            moodStr.contains("GOOD") -> Mood.GOOD
            moodStr.contains("OKAY") -> Mood.OKAY
            moodStr.contains("STRUGGLING") -> Mood.STRUGGLING
            moodStr.contains("DIFFICULT") -> Mood.DIFFICULT
            else -> null
        }
    }

    /**
     * Cancels the call without creating a journal entry.
     */
    fun cancelCall() {
        Log.d(TAG, "Call cancelled")
        vadManager.stopListening()
        ttsService.stopSpeaking()
        cleanup()

        callScope?.launch {
            _callEvents.emit(CallEvent.CallEnded(null))
        }
    }

    /**
     * Cleans up resources.
     */
    private fun cleanup() {
        durationJob?.cancel()
        callScope?.cancel()
        callScope = null
        conversationHistory.clear()
        messageHistory.clear()
        _callState.value = CallState.Idle
        _callDuration.value = 0
        vadManager.clearCache()
        ttsService.clearCache()
    }
}
