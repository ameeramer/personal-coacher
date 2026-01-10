package com.personalcoacher.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.domain.repository.SummaryRepository
import com.personalcoacher.notification.NotificationHelper
import com.personalcoacher.notification.NotificationScheduler
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
    val isError: Boolean = false,
    // API Key state
    val apiKeyInput: String = "",
    val hasApiKey: Boolean = false,
    val isSavingApiKey: Boolean = false,
    // Notification state
    val notificationsEnabled: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val reminderHour: Int = 22,
    val reminderMinute: Int = 15,
    val showTimePicker: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val chatRepository: ChatRepository,
    private val summaryRepository: SummaryRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val notificationHelper: NotificationHelper,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            currentUserId = tokenManager.currentUserId.first()
        }
        // Initialize API key state
        _uiState.update { it.copy(hasApiKey = tokenManager.hasClaudeApiKey()) }
        // Initialize notification state
        _uiState.update {
            it.copy(
                notificationsEnabled = tokenManager.getNotificationsEnabledSync(),
                hasNotificationPermission = notificationHelper.hasNotificationPermission(),
                reminderHour = tokenManager.getReminderHourSync(),
                reminderMinute = tokenManager.getReminderMinuteSync()
            )
        }
    }

    fun onApiKeyInputChange(value: String) {
        _uiState.update { it.copy(apiKeyInput = value) }
    }

    fun saveApiKey() {
        val apiKey = _uiState.value.apiKeyInput.trim()
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(message = "Please enter a valid API key", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingApiKey = true) }
            try {
                tokenManager.saveClaudeApiKey(apiKey)
                _uiState.update {
                    it.copy(
                        isSavingApiKey = false,
                        hasApiKey = true,
                        apiKeyInput = "",
                        message = "API key saved successfully",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingApiKey = false,
                        message = "Failed to save API key: ${e.localizedMessage}",
                        isError = true
                    )
                }
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            tokenManager.clearClaudeApiKey()
            _uiState.update {
                it.copy(
                    hasApiKey = false,
                    apiKeyInput = "",
                    message = "API key cleared",
                    isError = false
                )
            }
        }
    }

    fun downloadFromServer() {
        viewModelScope.launch {
            // Ensure userId is available
            if (currentUserId == null) {
                currentUserId = tokenManager.currentUserId.first()
            }
            val userId = currentUserId
            if (userId == null) {
                _uiState.update {
                    it.copy(isDownloading = false, message = "User not logged in", isError = true)
                }
                return@launch
            }

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
            // Ensure userId is available
            if (currentUserId == null) {
                currentUserId = tokenManager.currentUserId.first()
            }
            val userId = currentUserId
            if (userId == null) {
                _uiState.update {
                    it.copy(isUploading = false, message = "User not logged in", isError = true)
                }
                return@launch
            }

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

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            tokenManager.setNotificationsEnabled(enabled)
            if (enabled) {
                val hour = _uiState.value.reminderHour
                val minute = _uiState.value.reminderMinute
                notificationScheduler.scheduleJournalReminder(hour, minute)
                _uiState.update {
                    it.copy(
                        notificationsEnabled = true,
                        message = "Daily reminders enabled at ${formatTime(hour, minute)}",
                        isError = false
                    )
                }
            } else {
                notificationScheduler.cancelJournalReminder()
                _uiState.update {
                    it.copy(
                        notificationsEnabled = false,
                        message = "Daily reminders disabled",
                        isError = false
                    )
                }
            }
        }
    }

    fun refreshNotificationPermission() {
        _uiState.update {
            it.copy(hasNotificationPermission = notificationHelper.hasNotificationPermission())
        }
    }

    fun showTimePicker() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun hideTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            tokenManager.setReminderTime(hour, minute)
            _uiState.update {
                it.copy(
                    reminderHour = hour,
                    reminderMinute = minute,
                    showTimePicker = false
                )
            }
            // Reschedule notification if enabled
            if (_uiState.value.notificationsEnabled) {
                notificationScheduler.scheduleJournalReminder(hour, minute)
                _uiState.update {
                    it.copy(
                        message = "Reminder time updated to ${formatTime(hour, minute)}",
                        isError = false
                    )
                }
            }
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
}
