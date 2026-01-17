package com.personalcoacher.ui.screens.dailytools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.util.onError
import com.personalcoacher.util.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyToolsUiState())
    val uiState: StateFlow<DailyToolsUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadData()
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

    fun generateTodaysApp(forceRegenerate: Boolean = false) {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            val apiKey = tokenManager.getClaudeApiKeySync()
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(error = "Please configure your Claude API key in Settings") }
                return@launch
            }

            _uiState.update { it.copy(isGenerating = true, error = null) }

            repository.generateTodaysApp(userId, apiKey, forceRegenerate)
                .onSuccess { app ->
                    _uiState.update { it.copy(isGenerating = false, todaysApp = app) }
                }
                .onError { message ->
                    _uiState.update { it.copy(isGenerating = false, error = message) }
                }
        }
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
