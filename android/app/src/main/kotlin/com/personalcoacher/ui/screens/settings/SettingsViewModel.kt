package com.personalcoacher.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.entity.IntervalUnit
import com.personalcoacher.data.local.kuzu.KuzuDatabaseManager
import com.personalcoacher.data.local.kuzu.MigrationState
import com.personalcoacher.data.local.kuzu.MigrationStats
import com.personalcoacher.data.local.kuzu.RagMigrationService
import com.personalcoacher.domain.model.RuleType
import com.personalcoacher.domain.model.ScheduleRule
import com.personalcoacher.domain.repository.AgendaRepository
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.domain.repository.ChatRepository
import com.personalcoacher.domain.repository.DailyAppRepository
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.domain.repository.ScheduleRuleRepository
import com.personalcoacher.domain.repository.SummaryRepository
import com.personalcoacher.notification.DailyAppGenerationWorker
import com.personalcoacher.notification.NotificationHelper
import com.personalcoacher.notification.NotificationScheduler
import com.personalcoacher.util.DebugLogHelper
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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
    // Voyage API Key state
    val voyageApiKeyInput: String = "",
    val hasVoyageApiKey: Boolean = false,
    val isSavingVoyageApiKey: Boolean = false,
    // RAG Migration state
    val ragMigrationState: MigrationState = MigrationState.NotStarted,
    val isRagMigrated: Boolean = false,
    // Kuzu backup state
    val isExportingKuzu: Boolean = false,
    val isImportingKuzu: Boolean = false,
    val hasKuzuDatabase: Boolean = false,
    // Notification state
    val notificationsEnabled: Boolean = false,
    val dynamicNotificationsEnabled: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true, // For Android 12+ exact alarm permission
    val reminderHour: Int = 22,
    val reminderMinute: Int = 15,
    val showTimePicker: Boolean = false,
    // Debug log state
    val showDebugLog: Boolean = false,
    val debugLogContent: String = "",
    val workInfo: String = "",
    // Sync debug log state
    val showSyncDebugLog: Boolean = false,
    val syncDebugLogs: String = "",
    // Schedule rules state
    val scheduleRules: List<ScheduleRule> = emptyList(),
    val showAddScheduleRuleDialog: Boolean = false,
    val editingScheduleRule: ScheduleRule? = null,
    // Automatic Daily Tool state
    val autoDailyToolEnabled: Boolean = false,
    val dailyToolHour: Int = 8,
    val dailyToolMinute: Int = 0,
    val showDailyToolTimePicker: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val chatRepository: ChatRepository,
    private val summaryRepository: SummaryRepository,
    private val agendaRepository: AgendaRepository,
    private val dailyAppRepository: DailyAppRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val notificationHelper: NotificationHelper,
    private val notificationScheduler: NotificationScheduler,
    private val debugLogHelper: DebugLogHelper,
    private val scheduleRuleRepository: ScheduleRuleRepository,
    private val ragMigrationService: RagMigrationService,
    private val kuzuDatabaseManager: KuzuDatabaseManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            currentUserId = tokenManager.currentUserId.first()
            // Load schedule rules
            loadScheduleRules()
            // Observe RAG migration state
            ragMigrationService.migrationState.collect { state ->
                _uiState.update { it.copy(ragMigrationState = state) }
            }
        }
        // Initialize API key state
        _uiState.update {
            it.copy(
                hasApiKey = tokenManager.hasClaudeApiKey(),
                hasVoyageApiKey = tokenManager.hasVoyageApiKey(),
                isRagMigrated = tokenManager.getRagMigrationCompleteSync(),
                hasKuzuDatabase = kuzuDatabaseManager.databaseExists()
            )
        }
        // Initialize notification state
        _uiState.update {
            it.copy(
                notificationsEnabled = tokenManager.getNotificationsEnabledSync(),
                dynamicNotificationsEnabled = tokenManager.getDynamicNotificationsEnabledSync(),
                hasNotificationPermission = notificationHelper.hasNotificationPermission(),
                canScheduleExactAlarms = notificationHelper.canScheduleExactAlarms(),
                reminderHour = tokenManager.getReminderHourSync(),
                reminderMinute = tokenManager.getReminderMinuteSync(),
                autoDailyToolEnabled = tokenManager.getAutoDailyToolEnabledSync(),
                dailyToolHour = tokenManager.getDailyToolHourSync(),
                dailyToolMinute = tokenManager.getDailyToolMinuteSync()
            )
        }
        debugLogHelper.log("SettingsViewModel", "ViewModel initialized")
    }

    private fun loadScheduleRules() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            scheduleRuleRepository.getScheduleRules(userId).collect { rules ->
                _uiState.update { it.copy(scheduleRules = rules) }
            }
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
            val logs = StringBuilder()
            logs.appendLine("=== DOWNLOAD FROM SERVER ===")
            logs.appendLine("Time: ${java.time.LocalDateTime.now()}")

            // Get userId directly from SharedPreferences to avoid race conditions
            val userId = tokenManager.getUserId()
            if (userId != null) {
                currentUserId = userId
            }
            if (userId == null) {
                logs.appendLine("ERROR: User not logged in")
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        message = "User not logged in",
                        isError = true,
                        showSyncDebugLog = true,
                        syncDebugLogs = logs.toString()
                    )
                }
                return@launch
            }
            logs.appendLine("User ID: $userId")

            _uiState.update { it.copy(isDownloading = true, message = null) }

            // Download journal entries
            logs.appendLine("\n--- Journal Entries ---")
            val journalResult = try {
                journalRepository.syncEntries(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                Resource.error<Unit>("Journal sync failed: ${e.message}")
            }

            // Download conversations
            logs.appendLine("\n--- Conversations ---")
            val chatResult = try {
                chatRepository.syncConversations(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                Resource.error<Unit>("Chat sync failed: ${e.message}")
            }

            // Download summaries
            logs.appendLine("\n--- Summaries ---")
            val summaryResult = try {
                summaryRepository.syncSummaries(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                Resource.error<Unit>("Summary sync failed: ${e.message}")
            }

            // Download agenda items
            logs.appendLine("\n--- Agenda Items ---")
            val agendaResult = try {
                agendaRepository.syncAgendaItems(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                Resource.error<Unit>("Agenda sync failed: ${e.message}")
            }

            // Download daily tools
            logs.appendLine("\n--- Daily Tools ---")
            val dailyToolsResult = try {
                dailyAppRepository.downloadApps(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                Resource.error<Unit>("Daily tools sync failed: ${e.message}")
            }

            val errors = listOfNotNull(
                (journalResult as? Resource.Error)?.message,
                (chatResult as? Resource.Error)?.message,
                (summaryResult as? Resource.Error)?.message,
                (agendaResult as? Resource.Error)?.message,
                (dailyToolsResult as? Resource.Error)?.message
            )

            val successCount = listOf(journalResult, chatResult, summaryResult, agendaResult, dailyToolsResult)
                .count { it.isSuccess() }
            val failCount = 5 - successCount

            logs.appendLine("\n=== SUMMARY ===")
            logs.appendLine("Success: $successCount, Failed: $failCount")

            _uiState.update {
                if (errors.isEmpty()) {
                    it.copy(
                        isDownloading = false,
                        message = "Downloaded all data from server",
                        isError = false,
                        showSyncDebugLog = true,
                        syncDebugLogs = logs.toString()
                    )
                } else {
                    it.copy(
                        isDownloading = false,
                        message = errors.joinToString("\n"),
                        isError = true,
                        showSyncDebugLog = true,
                        syncDebugLogs = logs.toString()
                    )
                }
            }
        }
    }

    fun backupToServer() {
        viewModelScope.launch {
            val logs = StringBuilder()
            logs.appendLine("=== BACKUP TO SERVER ===")
            logs.appendLine("Time: ${java.time.LocalDateTime.now()}")

            // Get userId directly from SharedPreferences to avoid race conditions
            val userId = tokenManager.getUserId()
            if (userId != null) {
                currentUserId = userId
            }
            if (userId == null) {
                logs.appendLine("ERROR: User not logged in")
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        message = "User not logged in",
                        isError = true,
                        showSyncDebugLog = true,
                        syncDebugLogs = logs.toString()
                    )
                }
                return@launch
            }
            logs.appendLine("User ID: $userId")

            _uiState.update { it.copy(isUploading = true, message = null) }

            // Upload journal entries
            logs.appendLine("\n--- Journal Entries ---")
            val journalResult = try {
                journalRepository.uploadEntries(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                logs.appendLine("Stack trace: ${e.stackTraceToString().take(500)}")
                Resource.error<Unit>("Journal upload failed: ${e.message}")
            }

            // Upload conversations (messages are already synced via chat API)
            logs.appendLine("\n--- Conversations ---")
            val chatResult = try {
                chatRepository.uploadConversations(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                logs.appendLine("Stack trace: ${e.stackTraceToString().take(500)}")
                Resource.error<Unit>("Chat upload failed: ${e.message}")
            }

            // Upload summaries
            logs.appendLine("\n--- Summaries ---")
            val summaryResult = try {
                summaryRepository.uploadSummaries(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                logs.appendLine("Stack trace: ${e.stackTraceToString().take(500)}")
                Resource.error<Unit>("Summary upload failed: ${e.message}")
            }

            // Upload agenda items
            logs.appendLine("\n--- Agenda Items ---")
            val agendaResult = try {
                agendaRepository.uploadAgendaItems(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                logs.appendLine("Stack trace: ${e.stackTraceToString().take(500)}")
                Resource.error<Unit>("Agenda upload failed: ${e.message}")
            }

            // Upload daily tools
            logs.appendLine("\n--- Daily Tools ---")
            val dailyToolsResult = try {
                dailyAppRepository.uploadApps(userId).also {
                    logs.appendLine("Result: ${if (it.isSuccess()) "SUCCESS" else "ERROR: ${(it as? Resource.Error)?.message}"}")
                }
            } catch (e: Exception) {
                logs.appendLine("Exception: ${e.message}")
                logs.appendLine("Stack trace: ${e.stackTraceToString().take(500)}")
                Resource.error<Unit>("Daily tools upload failed: ${e.message}")
            }

            val errors = listOfNotNull(
                (journalResult as? Resource.Error)?.message,
                (chatResult as? Resource.Error)?.message,
                (summaryResult as? Resource.Error)?.message,
                (agendaResult as? Resource.Error)?.message,
                (dailyToolsResult as? Resource.Error)?.message
            )

            val successCount = listOf(journalResult, chatResult, summaryResult, agendaResult, dailyToolsResult)
                .count { it.isSuccess() }
            val failCount = 5 - successCount

            logs.appendLine("\n=== SUMMARY ===")
            logs.appendLine("Success: $successCount, Failed: $failCount")
            if (errors.isNotEmpty()) {
                logs.appendLine("Errors:")
                errors.forEach { logs.appendLine("  - $it") }
            }

            _uiState.update {
                if (errors.isEmpty()) {
                    it.copy(
                        isUploading = false,
                        message = "Backup completed successfully",
                        isError = false,
                        showSyncDebugLog = true,
                        syncDebugLogs = logs.toString()
                    )
                } else {
                    it.copy(
                        isUploading = false,
                        message = errors.joinToString("\n"),
                        isError = true,
                        showSyncDebugLog = true,
                        syncDebugLogs = logs.toString()
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
        debugLogHelper.log("SettingsViewModel", "toggleNotifications($enabled) called")
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
            it.copy(
                hasNotificationPermission = notificationHelper.hasNotificationPermission(),
                canScheduleExactAlarms = notificationHelper.canScheduleExactAlarms()
            )
        }
    }

    fun toggleDynamicNotifications(enabled: Boolean) {
        debugLogHelper.log("SettingsViewModel", "toggleDynamicNotifications($enabled) called")
        viewModelScope.launch {
            tokenManager.setDynamicNotificationsEnabled(enabled)
            if (enabled) {
                val userId = currentUserId
                if (userId != null) {
                    // Check if user has any rules - if not, create the default 6-hour rule
                    val hasRules = scheduleRuleRepository.hasScheduleRules(userId)
                    if (!hasRules) {
                        debugLogHelper.log("SettingsViewModel", "No schedule rules found, creating default 6-hour rule")
                        val defaultRule = ScheduleRule(
                            id = DEFAULT_RULE_ID,
                            userId = userId,
                            type = RuleType.Interval(6, IntervalUnit.HOURS),
                            enabled = true
                        )
                        scheduleRuleRepository.addScheduleRule(defaultRule)
                        // Schedule the default rule
                        notificationScheduler.scheduleRule(defaultRule)
                    } else {
                        // Schedule all existing enabled rules
                        val rules = scheduleRuleRepository.getEnabledScheduleRulesSync(userId)
                        notificationScheduler.scheduleFromRules(rules)
                    }
                }

                // Trigger an immediate notification for feedback
                notificationScheduler.scheduleDynamicNotifications()

                _uiState.update {
                    it.copy(
                        dynamicNotificationsEnabled = true,
                        message = "AI Coach check-ins enabled",
                        isError = false
                    )
                }
            } else {
                notificationScheduler.cancelDynamicNotifications()
                // Also cancel any custom schedule rules
                val userId = currentUserId
                if (userId != null) {
                    val rules = scheduleRuleRepository.getEnabledScheduleRulesSync(userId)
                    rules.forEach { rule ->
                        notificationScheduler.cancelRule(rule.id)
                    }
                }
                _uiState.update {
                    it.copy(
                        dynamicNotificationsEnabled = false,
                        message = "AI Coach check-ins disabled",
                        isError = false
                    )
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_RULE_ID = "default_6_hour_rule"
    }

    fun showTimePicker() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun hideTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        debugLogHelper.log("SettingsViewModel", "setReminderTime($hour, $minute) called")
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

    fun testNotification() {
        debugLogHelper.log("SettingsViewModel", "testNotification() called")
        viewModelScope.launch {
            val result = notificationHelper.showJournalReminderNotification()
            debugLogHelper.log("SettingsViewModel", "Test notification result: $result")
            _uiState.update {
                it.copy(
                    message = result,
                    isError = result.startsWith("FAILED") || result.startsWith("EXCEPTION")
                )
            }
        }
    }

    fun showDebugLog() {
        debugLogHelper.log("SettingsViewModel", "showDebugLog() called")
        viewModelScope.launch {
            val logs = debugLogHelper.getLogs()
            val workInfo = withContext(Dispatchers.IO) {
                notificationScheduler.getScheduledWorkInfo()
            }
            _uiState.update {
                it.copy(
                    showDebugLog = true,
                    debugLogContent = logs,
                    workInfo = workInfo
                )
            }
        }
    }

    fun hideDebugLog() {
        _uiState.update { it.copy(showDebugLog = false) }
    }

    fun hideSyncDebugLog() {
        _uiState.update { it.copy(showSyncDebugLog = false) }
    }

    fun refreshDebugLog() {
        debugLogHelper.log("SettingsViewModel", "refreshDebugLog() called")
        viewModelScope.launch {
            val logs = debugLogHelper.getLogs()
            val workInfo = withContext(Dispatchers.IO) {
                notificationScheduler.getScheduledWorkInfo()
            }
            _uiState.update {
                it.copy(
                    debugLogContent = logs,
                    workInfo = workInfo
                )
            }
        }
    }

    fun clearDebugLog() {
        debugLogHelper.clear()
        debugLogHelper.log("SettingsViewModel", "Debug log cleared")
        _uiState.update { it.copy(debugLogContent = debugLogHelper.getLogs()) }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    // Schedule Rules Management

    fun showAddScheduleRuleDialog() {
        _uiState.update { it.copy(showAddScheduleRuleDialog = true, editingScheduleRule = null) }
    }

    fun showEditScheduleRuleDialog(rule: ScheduleRule) {
        _uiState.update { it.copy(showAddScheduleRuleDialog = true, editingScheduleRule = rule) }
    }

    fun hideScheduleRuleDialog() {
        _uiState.update { it.copy(showAddScheduleRuleDialog = false, editingScheduleRule = null) }
    }

    fun saveScheduleRule(rule: ScheduleRule) {
        debugLogHelper.log("SettingsViewModel", "saveScheduleRule() called for rule: ${rule.id}")
        viewModelScope.launch {
            try {
                scheduleRuleRepository.addScheduleRule(rule)

                // Reschedule notifications with all rules
                val allRules = scheduleRuleRepository.getEnabledScheduleRulesSync(rule.userId)
                notificationScheduler.scheduleFromRules(allRules)

                _uiState.update {
                    it.copy(
                        showAddScheduleRuleDialog = false,
                        editingScheduleRule = null,
                        message = "Schedule saved",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                debugLogHelper.log("SettingsViewModel", "Error saving schedule rule: ${e.message}")
                _uiState.update {
                    it.copy(
                        message = "Failed to save schedule: ${e.localizedMessage}",
                        isError = true
                    )
                }
            }
        }
    }

    fun deleteScheduleRule(rule: ScheduleRule) {
        debugLogHelper.log("SettingsViewModel", "deleteScheduleRule() called for rule: ${rule.id}")
        viewModelScope.launch {
            try {
                // Cancel the worker for this rule
                notificationScheduler.cancelRule(rule.id)

                // Delete from database
                scheduleRuleRepository.deleteScheduleRule(rule.id)

                _uiState.update {
                    it.copy(
                        message = "Schedule deleted",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                debugLogHelper.log("SettingsViewModel", "Error deleting schedule rule: ${e.message}")
                _uiState.update {
                    it.copy(
                        message = "Failed to delete schedule: ${e.localizedMessage}",
                        isError = true
                    )
                }
            }
        }
    }

    fun toggleScheduleRuleEnabled(rule: ScheduleRule) {
        debugLogHelper.log("SettingsViewModel", "toggleScheduleRuleEnabled() for rule: ${rule.id}, current: ${rule.enabled}")
        viewModelScope.launch {
            try {
                val newEnabled = !rule.enabled
                scheduleRuleRepository.setScheduleRuleEnabled(rule.id, newEnabled)

                if (newEnabled) {
                    // Re-schedule this rule
                    val updatedRule = rule.copy(enabled = true)
                    notificationScheduler.scheduleRule(updatedRule)
                } else {
                    // Cancel this rule's worker
                    notificationScheduler.cancelRule(rule.id)
                }

                _uiState.update {
                    it.copy(
                        message = if (newEnabled) "Schedule enabled" else "Schedule disabled",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                debugLogHelper.log("SettingsViewModel", "Error toggling schedule rule: ${e.message}")
                _uiState.update {
                    it.copy(
                        message = "Failed to update schedule: ${e.localizedMessage}",
                        isError = true
                    )
                }
            }
        }
    }

    fun getUserId(): String? = currentUserId

    // Automatic Daily Tool methods

    fun toggleAutoDailyTool(enabled: Boolean) {
        debugLogHelper.log("SettingsViewModel", "toggleAutoDailyTool($enabled) called")
        viewModelScope.launch {
            tokenManager.setAutoDailyToolEnabled(enabled)
            if (enabled) {
                val hour = _uiState.value.dailyToolHour
                val minute = _uiState.value.dailyToolMinute
                DailyAppGenerationWorker.scheduleDaily(context, hour, minute)
                _uiState.update {
                    it.copy(
                        autoDailyToolEnabled = true,
                        message = "Automatic daily tool enabled at ${formatTime(hour, minute)}",
                        isError = false
                    )
                }
            } else {
                DailyAppGenerationWorker.cancel(context)
                _uiState.update {
                    it.copy(
                        autoDailyToolEnabled = false,
                        message = "Automatic daily tool disabled",
                        isError = false
                    )
                }
            }
        }
    }

    fun showDailyToolTimePicker() {
        _uiState.update { it.copy(showDailyToolTimePicker = true) }
    }

    fun hideDailyToolTimePicker() {
        _uiState.update { it.copy(showDailyToolTimePicker = false) }
    }

    fun setDailyToolTime(hour: Int, minute: Int) {
        debugLogHelper.log("SettingsViewModel", "setDailyToolTime($hour, $minute) called")
        viewModelScope.launch {
            tokenManager.setDailyToolTime(hour, minute)
            _uiState.update {
                it.copy(
                    dailyToolHour = hour,
                    dailyToolMinute = minute,
                    showDailyToolTimePicker = false
                )
            }
            // Reschedule if enabled
            if (_uiState.value.autoDailyToolEnabled) {
                DailyAppGenerationWorker.scheduleDaily(context, hour, minute)
                _uiState.update {
                    it.copy(
                        message = "Daily tool time updated to ${formatTime(hour, minute)}",
                        isError = false
                    )
                }
            }
        }
    }

    // Voyage API Key methods

    fun onVoyageApiKeyInputChange(value: String) {
        _uiState.update { it.copy(voyageApiKeyInput = value) }
    }

    fun saveVoyageApiKey() {
        val apiKey = _uiState.value.voyageApiKeyInput.trim()
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(message = "Please enter a valid Voyage API key", isError = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingVoyageApiKey = true) }
            try {
                tokenManager.saveVoyageApiKey(apiKey)
                _uiState.update {
                    it.copy(
                        isSavingVoyageApiKey = false,
                        hasVoyageApiKey = true,
                        voyageApiKeyInput = "",
                        message = "Voyage API key saved successfully",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingVoyageApiKey = false,
                        message = "Failed to save Voyage API key: ${e.localizedMessage}",
                        isError = true
                    )
                }
            }
        }
    }

    fun clearVoyageApiKey() {
        viewModelScope.launch {
            tokenManager.clearVoyageApiKey()
            _uiState.update {
                it.copy(
                    hasVoyageApiKey = false,
                    voyageApiKeyInput = "",
                    message = "Voyage API key cleared",
                    isError = false
                )
            }
        }
    }

    // RAG Migration methods

    fun startRagMigration() {
        val userId = currentUserId ?: run {
            _uiState.update {
                it.copy(message = "User not logged in", isError = true)
            }
            return
        }

        if (!tokenManager.hasVoyageApiKey()) {
            _uiState.update {
                it.copy(message = "Please configure Voyage API key first", isError = true)
            }
            return
        }

        debugLogHelper.log("SettingsViewModel", "startRagMigration() called for user: $userId")
        viewModelScope.launch {
            ragMigrationService.startMigration(userId)
            // Update UI state after migration
            _uiState.update {
                it.copy(
                    isRagMigrated = tokenManager.getRagMigrationCompleteSync(),
                    hasKuzuDatabase = kuzuDatabaseManager.databaseExists()
                )
            }
        }
    }

    // Kuzu Database Backup methods

    fun exportKuzuDatabase(outputUri: Uri) {
        debugLogHelper.log("SettingsViewModel", "exportKuzuDatabase() called")
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingKuzu = true, message = null) }

            val result = kuzuDatabaseManager.exportToUri(outputUri)

            result.fold(
                onSuccess = {
                    debugLogHelper.log("SettingsViewModel", "Kuzu database exported successfully")
                    _uiState.update {
                        it.copy(
                            isExportingKuzu = false,
                            message = "Knowledge graph backup exported successfully",
                            isError = false
                        )
                    }
                },
                onFailure = { error ->
                    debugLogHelper.log("SettingsViewModel", "Kuzu export failed: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isExportingKuzu = false,
                            message = "Export failed: ${error.localizedMessage}",
                            isError = true
                        )
                    }
                }
            )
        }
    }

    fun importKuzuDatabase(inputUri: Uri) {
        debugLogHelper.log("SettingsViewModel", "importKuzuDatabase() called")
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingKuzu = true, message = null) }

            val result = kuzuDatabaseManager.importFromUri(inputUri)

            result.fold(
                onSuccess = {
                    debugLogHelper.log("SettingsViewModel", "Kuzu database imported successfully")
                    // Mark migration as complete since we imported a valid database
                    tokenManager.setRagMigrationComplete(true)
                    _uiState.update {
                        it.copy(
                            isImportingKuzu = false,
                            hasKuzuDatabase = true,
                            isRagMigrated = true,
                            message = "Knowledge graph backup restored successfully",
                            isError = false
                        )
                    }
                },
                onFailure = { error ->
                    debugLogHelper.log("SettingsViewModel", "Kuzu import failed: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isImportingKuzu = false,
                            hasKuzuDatabase = kuzuDatabaseManager.databaseExists(),
                            message = "Import failed: ${error.localizedMessage}",
                            isError = true
                        )
                    }
                }
            )
        }
    }

    fun refreshKuzuDatabaseState() {
        _uiState.update {
            it.copy(hasKuzuDatabase = kuzuDatabaseManager.databaseExists())
        }
    }
}
