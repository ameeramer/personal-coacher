package com.personalcoacher.ui.screens.dailytools

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.notification.DailyAppGenerationWorker
import com.personalcoacher.util.DailyToolLogBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DailyToolsUiState(
    val todaysApp: DailyApp? = null,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val isRefining: Boolean = false,
    val error: String? = null,
    val hasApiKey: Boolean = false,
    val likedAppCount: Int = 0,
    val showDebugLog: Boolean = false,
    val debugLogContent: String = "",
    val showEditDialog: Boolean = false,
    val editFeedback: String = ""
)

@HiltViewModel
class DailyToolsViewModel @Inject constructor(
    private val repository: DailyAppRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyToolsUiState())
    val uiState: StateFlow<DailyToolsUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    // Store references for cleanup to prevent memory leaks
    private var workInfoLiveData: LiveData<List<WorkInfo>>? = null
    private val workInfoObserver = Observer<List<WorkInfo>> { workInfos ->
        val isGenerating = workInfos?.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        } ?: false
        _uiState.update { it.copy(isGenerating = isGenerating) }
    }

    init {
        loadData()
        observeGenerationWorker()
    }

    private fun loadData() {
        viewModelScope.launch {
            val userId = tokenManager.awaitUserId()
            val apiKey = tokenManager.getClaudeApiKeySync()

            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                return@launch
            }

            currentUserId = userId
            _uiState.update { it.copy(hasApiKey = !apiKey.isNullOrBlank()) }

            // Load liked app count
            val likedCount = repository.getLikedAppCount(userId)
            _uiState.update { it.copy(likedAppCount = likedCount) }

            // Subscribe to today's app
            repository.getTodaysApp(userId).collect { app ->
                _uiState.update {
                    it.copy(
                        todaysApp = app,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Observe the WorkManager to track generation status.
     * This allows the UI to show generating state even if the user leaves and returns.
     */
    private fun observeGenerationWorker() {
        workInfoLiveData = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("daily_app_generation_one_time")
        workInfoLiveData?.observeForever(workInfoObserver)
    }

    override fun onCleared() {
        super.onCleared()
        // Remove observer to prevent memory leak
        workInfoLiveData?.removeObserver(workInfoObserver)
    }

    /**
     * Start generating today's app in the background.
     * The generation continues even if the user leaves the app or screen.
     * A notification will be shown when generation completes.
     */
    fun generateTodaysApp(forceRegenerate: Boolean = false) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        DailyToolLogBuffer.log("[$timestamp] === Generate Button Clicked ===")
        DailyToolLogBuffer.log("[$timestamp] forceRegenerate=$forceRegenerate")
        Log.i(TAG, "=== Generate Button Clicked ===")
        Log.i(TAG, "forceRegenerate=$forceRegenerate")

        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            DailyToolLogBuffer.log("[$timestamp] ERROR: No Claude API key configured")
            Log.e(TAG, "No Claude API key configured")
            _uiState.update { it.copy(error = "Please configure your Claude API key in Settings") }
            return
        }

        DailyToolLogBuffer.log("[$timestamp] API key found, starting cloud generation (QStash)...")
        Log.i(TAG, "API key found, starting cloud generation (QStash)...")
        _uiState.update { it.copy(isGenerating = true, error = null) }

        // Start background worker - this continues even if user leaves the app
        // useLocalFallback=false means we ONLY use QStash and fail if it doesn't work
        DailyAppGenerationWorker.startOneTimeGeneration(
            context = context,
            forceRegenerate = forceRegenerate,
            showNotification = true,
            useLocalFallback = false  // QStash only - no local fallback
        )
        DailyToolLogBuffer.log("[$timestamp] WorkManager job enqueued")
        Log.i(TAG, "WorkManager job enqueued")
    }

    /**
     * Show the debug log dialog with all collected logs.
     */
    fun showDebugLog() {
        _uiState.update { it.copy(
            showDebugLog = true,
            debugLogContent = DailyToolLogBuffer.getLogs()
        ) }
    }

    /**
     * Hide the debug log dialog.
     */
    fun hideDebugLog() {
        _uiState.update { it.copy(showDebugLog = false) }
    }

    /**
     * Clear all debug logs.
     */
    fun clearDebugLog() {
        DailyToolLogBuffer.clear()
        _uiState.update { it.copy(debugLogContent = "") }
    }

    /**
     * Refresh the debug log content.
     */
    fun refreshDebugLog() {
        _uiState.update { it.copy(debugLogContent = DailyToolLogBuffer.getLogs()) }
    }

    companion object {
        private const val TAG = "DailyToolsViewModel"
    }

    fun likeApp(appId: String) {
        viewModelScope.launch {
            repository.updateAppStatus(appId, DailyAppStatus.LIKED)
            // Update liked count
            currentUserId?.let { userId ->
                val count = repository.getLikedAppCount(userId)
                _uiState.update { it.copy(likedAppCount = count) }
            }
        }
    }

    fun dislikeApp(appId: String) {
        viewModelScope.launch {
            repository.updateAppStatus(appId, DailyAppStatus.DISLIKED)
        }
    }

    fun markAppAsUsed(appId: String) {
        viewModelScope.launch {
            repository.markAppAsUsed(appId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshApiKeyStatus() {
        viewModelScope.launch {
            val apiKey = tokenManager.getClaudeApiKeySync()
            _uiState.update { it.copy(hasApiKey = !apiKey.isNullOrBlank()) }
        }
    }

    // ==================== Edit/Refine Functionality ====================

    /**
     * Show the edit dialog for the current app.
     */
    fun showEditDialog() {
        _uiState.update { it.copy(showEditDialog = true, editFeedback = "") }
    }

    /**
     * Hide the edit dialog.
     */
    fun hideEditDialog() {
        _uiState.update { it.copy(showEditDialog = false, editFeedback = "") }
    }

    /**
     * Update the feedback text in the edit dialog.
     */
    fun updateEditFeedback(feedback: String) {
        _uiState.update { it.copy(editFeedback = feedback) }
    }

    /**
     * Refine the current app based on user feedback.
     */
    fun refineApp(appId: String) {
        val feedback = _uiState.value.editFeedback.trim()
        if (feedback.isBlank()) {
            _uiState.update { it.copy(error = "Please describe what you'd like to change") }
            return
        }

        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Please configure your Claude API key in Settings") }
            return
        }

        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        DailyToolLogBuffer.log("[$timestamp] === Refine Button Clicked ===")
        DailyToolLogBuffer.log("[$timestamp] appId=$appId, feedback=$feedback")
        Log.i(TAG, "=== Refine Button Clicked ===")
        Log.i(TAG, "appId=$appId, feedback=$feedback")

        _uiState.update { it.copy(isRefining = true, showEditDialog = false, error = null) }

        viewModelScope.launch {
            DailyToolLogBuffer.log("[$timestamp] Starting refinement...")
            Log.i(TAG, "Starting refinement...")

            val result = repository.refineApp(appId, feedback, apiKey)

            when (result) {
                is com.personalcoacher.util.Resource.Success -> {
                    DailyToolLogBuffer.log("[$timestamp] Refinement successful")
                    Log.i(TAG, "Refinement successful")
                    _uiState.update { it.copy(isRefining = false, editFeedback = "") }
                }
                is com.personalcoacher.util.Resource.Error -> {
                    val errorMsg = result.message ?: "Failed to refine app"
                    DailyToolLogBuffer.log("[$timestamp] Refinement failed: $errorMsg")
                    Log.e(TAG, "Refinement failed: $errorMsg")
                    _uiState.update { it.copy(isRefining = false, error = errorMsg) }
                }
                is com.personalcoacher.util.Resource.Loading -> {
                    // Should not happen for suspend functions
                }
            }
        }
    }
}
