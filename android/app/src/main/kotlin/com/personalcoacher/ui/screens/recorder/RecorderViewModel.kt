package com.personalcoacher.ui.screens.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.remote.GeminiTranscriptionService
import com.personalcoacher.domain.model.RecordingSession
import com.personalcoacher.domain.model.RecordingSessionStatus
import com.personalcoacher.domain.model.Transcription
import com.personalcoacher.domain.repository.RecorderRepository
import com.personalcoacher.recorder.AudioRecorderService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecorderUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val currentChunkElapsed: Int = 0,
    val totalElapsed: Int = 0,
    val currentChunkIndex: Int = 0,
    val chunkDuration: Int = 1800, // 30 minutes default
    val selectedSessionId: String? = null,
    val error: String? = null,
    val hasPermission: Boolean? = null,
    val geminiApiKey: String = "",
    val selectedGeminiModel: String = GeminiTranscriptionService.DEFAULT_MODEL,
    val customModelId: String = ""
)

@HiltViewModel
class RecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recorderRepository: RecorderRepository,
    private val tokenManager: TokenManager,
    private val geminiService: GeminiTranscriptionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

    private val _selectedSessionId = MutableStateFlow<String?>(null)

    private var audioRecorderService: AudioRecorderService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioRecorderService.LocalBinder
            audioRecorderService = binder.getService()
            serviceBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioRecorderService = null
            serviceBound = false
        }
    }

    // Get current user ID from token
    private val userId: StateFlow<String?> = tokenManager.currentUserId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // All sessions for the current user
    val sessions: StateFlow<List<RecordingSession>> = userId.flatMapLatest { uid ->
        uid?.let { recorderRepository.getSessionsByUser(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transcriptions for the selected session
    val selectedSessionTranscriptions: StateFlow<List<Transcription>> = _selectedSessionId.flatMapLatest { sessionId ->
        sessionId?.let { recorderRepository.getTranscriptionsBySession(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected session with transcriptions
    val selectedSession: StateFlow<RecordingSession?> = _selectedSessionId.flatMapLatest { sessionId ->
        sessionId?.let { recorderRepository.getSessionWithTranscriptions(it) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        bindService()
        loadSavedApiKey()
    }

    private fun loadSavedApiKey() {
        viewModelScope.launch {
            tokenManager.geminiApiKey.collect { apiKey ->
                if (!apiKey.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(geminiApiKey = apiKey)
                    geminiService.setApiKey(apiKey)
                }
            }
        }
    }

    private fun bindService() {
        val intent = Intent(context, AudioRecorderService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeServiceState() {
        val service = audioRecorderService ?: return

        viewModelScope.launch {
            combine(
                service.isRecording,
                service.isPaused,
                service.currentChunkElapsed,
                service.totalElapsed,
                service.currentChunkIndex,
                service.error
            ) { values: Array<Any?> ->
                val isRecording = values[0] as Boolean
                val isPaused = values[1] as Boolean
                val chunkElapsed = values[2] as Int
                val totalElapsed = values[3] as Int
                val chunkIndex = values[4] as Int
                val error = values[5] as String?
                _uiState.value.copy(
                    isRecording = isRecording,
                    isPaused = isPaused,
                    currentChunkElapsed = chunkElapsed,
                    totalElapsed = totalElapsed,
                    currentChunkIndex = chunkIndex,
                    error = error
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            tokenManager.saveGeminiApiKey(apiKey)
            _uiState.value = _uiState.value.copy(geminiApiKey = apiKey)
            geminiService.setApiKey(apiKey)
        }
    }

    fun setGeminiModel(modelId: String) {
        _uiState.value = _uiState.value.copy(selectedGeminiModel = modelId)
        // For custom model, use the customModelId; otherwise use the selected model
        val effectiveModelId = if (modelId == GeminiTranscriptionService.CUSTOM_MODEL_ID) {
            _uiState.value.customModelId.ifBlank { GeminiTranscriptionService.DEFAULT_MODEL }
        } else {
            modelId
        }
        geminiService.setModel(effectiveModelId)
    }

    fun setCustomModelId(customModelId: String) {
        _uiState.value = _uiState.value.copy(customModelId = customModelId)
        // If custom model is currently selected, update the service
        if (_uiState.value.selectedGeminiModel == GeminiTranscriptionService.CUSTOM_MODEL_ID) {
            geminiService.setModel(customModelId.ifBlank { GeminiTranscriptionService.DEFAULT_MODEL })
        }
    }

    fun setChunkDuration(durationSeconds: Int) {
        _uiState.value = _uiState.value.copy(chunkDuration = durationSeconds)
    }

    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
        _uiState.value = _uiState.value.copy(selectedSessionId = sessionId)
    }

    fun startRecording() {
        viewModelScope.launch {
            val uid = userId.value ?: run {
                _uiState.value = _uiState.value.copy(error = "User not logged in")
                return@launch
            }

            val apiKey = _uiState.value.geminiApiKey
            if (apiKey.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "Please set your Gemini API key first")
                return@launch
            }

            try {
                // Create a new session
                val session = recorderRepository.createSession(
                    userId = uid,
                    chunkDuration = _uiState.value.chunkDuration
                )

                _selectedSessionId.value = session.id
                _uiState.value = _uiState.value.copy(selectedSessionId = session.id, error = null)

                // Start the recording service
                val intent = Intent(context, AudioRecorderService::class.java).apply {
                    action = AudioRecorderService.ACTION_START
                    putExtra(AudioRecorderService.EXTRA_SESSION_ID, session.id)
                    putExtra(AudioRecorderService.EXTRA_USER_ID, uid)
                    putExtra(AudioRecorderService.EXTRA_CHUNK_DURATION, _uiState.value.chunkDuration)
                }
                context.startForegroundService(intent)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to start recording")
            }
        }
    }

    fun stopRecording() {
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun pauseRecording() {
        audioRecorderService?.pauseRecording()
    }

    fun resumeRecording() {
        audioRecorderService?.resumeRecording()
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                recorderRepository.deleteSession(sessionId)
                if (_selectedSessionId.value == sessionId) {
                    _selectedSessionId.value = null
                    _uiState.value = _uiState.value.copy(selectedSessionId = null)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to delete session")
            }
        }
    }

    fun updateSessionTitle(sessionId: String, title: String) {
        viewModelScope.launch {
            try {
                recorderRepository.updateSessionTitle(sessionId, title)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update title")
            }
        }
    }

    fun setPermissionStatus(hasPermission: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = hasPermission)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
