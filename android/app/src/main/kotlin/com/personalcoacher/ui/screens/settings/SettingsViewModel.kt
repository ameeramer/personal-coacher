package com.personalcoacher.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.domain.repository.SummaryRepository
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDownloading: Boolean = false,
    val isUploading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val chatRepository: ChatRepository,
    private val summaryRepository: SummaryRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            currentUserId = tokenManager.currentUserId.first()
        }
    }

    fun downloadFromServer() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch

            _uiState.update { it.copy(isDownloading = true, message = null) }

            // Download journal entries
            val journalResult = journalRepository.syncEntries(userId)

            // Download conversations
            val chatResult = chatRepository.syncConversations(userId)

            // Download summaries
            val summaryResult = summaryRepository.syncSummaries(userId)

            val errors = listOfNotNull(
                (journalResult as? Resource.Error)?.message,
                (chatResult as? Resource.Error)?.message,
                (summaryResult as? Resource.Error)?.message
            )

            _uiState.update {
                if (errors.isEmpty()) {
                    it.copy(
                        isDownloading = false,
                        message = "Downloaded all data from server",
                        isError = false
                    )
                } else {
                    it.copy(
                        isDownloading = false,
                        message = errors.joinToString("\n"),
                        isError = true
                    )
                }
            }
        }
    }

    fun backupToServer() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch

            _uiState.update { it.copy(isUploading = true, message = null) }

            // Upload journal entries
            val journalResult = journalRepository.uploadEntries(userId)

            // Upload conversations (messages are already synced via chat API)
            val chatResult = chatRepository.uploadConversations(userId)

            val errors = listOfNotNull(
                (journalResult as? Resource.Error)?.message,
                (chatResult as? Resource.Error)?.message
            )

            _uiState.update {
                if (errors.isEmpty()) {
                    it.copy(
                        isUploading = false,
                        message = "Backup completed successfully",
                        isError = false
                    )
                } else {
                    it.copy(
                        isUploading = false,
                        message = errors.joinToString("\n"),
                        isError = true
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
