package com.personalcoacher.ui.screens.dailytools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.DailyApp
import com.personalcoacher.domain.model.DailyAppStatus
import com.personalcoacher.domain.repository.DailyAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyToolsUiState(
    val likedApps: List<DailyApp> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class MyToolsViewModel @Inject constructor(
    private val repository: DailyAppRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyToolsUiState())
    val uiState: StateFlow<MyToolsUiState> = _uiState.asStateFlow()

    init {
        loadLikedApps()
    }

    private fun loadLikedApps() {
        viewModelScope.launch {
            val userId = tokenManager.awaitUserId()

            if (userId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            repository.getLikedApps(userId).collect { apps ->
                _uiState.update {
                    it.copy(
                        likedApps = apps,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun unlikeApp(appId: String) {
        viewModelScope.launch {
            repository.updateAppStatus(appId, DailyAppStatus.PENDING)
        }
    }

    fun deleteApp(appId: String) {
        viewModelScope.launch {
            repository.deleteApp(appId)
        }
    }

    fun markAppAsUsed(appId: String) {
        viewModelScope.launch {
            repository.markAppAsUsed(appId)
        }
    }
}
