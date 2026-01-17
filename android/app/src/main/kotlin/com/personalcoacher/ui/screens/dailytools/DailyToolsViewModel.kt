package com.personalcoacher.ui.screens.dailytools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.notification.DailyAppGenerationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyToolsUiState(
    val todaysApp: DailyApp? = null,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val hasApiKey: Boolean = false,
    val likedAppCount: Int = 0
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
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData("daily_app_generation_one_time")
                .observeForever { workInfos ->
                    val isGenerating = workInfos?.any {
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                    } ?: false

                    _uiState.update { it.copy(isGenerating = isGenerating) }
                }
        }
    }

    /**
     * Start generating today's app in the background.
     * The generation continues even if the user leaves the app or screen.
     * A notification will be shown when generation completes.
     */
    fun generateTodaysApp(forceRegenerate: Boolean = false) {
        val apiKey = tokenManager.getClaudeApiKeySync()
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Please configure your Claude API key in Settings") }
            return
        }

        _uiState.update { it.copy(isGenerating = true, error = null) }

        // Start background worker - this continues even if user leaves the app
        DailyAppGenerationWorker.startOneTimeGeneration(
            context = context,
            forceRegenerate = forceRegenerate,
            showNotification = true
        )
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
}
