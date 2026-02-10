package com.personalcoacher.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isLoggedIn = MutableStateFlow(getTokenSync() != null)
    val isLoggedIn: Flow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUserId = MutableStateFlow(getUserId())
    val currentUserId: Flow<String?> = _currentUserId.asStateFlow()

    suspend fun saveToken(token: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
        _isLoggedIn.value = true
    }

    fun getTokenSync(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }

    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_TOKEN, null)
    }

    suspend fun saveUserId(userId: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
        _currentUserId.value = userId
    }

    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    suspend fun saveUserEmail(email: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_USER_EMAIL, email).apply()
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    // Claude API Key management
    private val _claudeApiKey = MutableStateFlow(getClaudeApiKeySync())
    val claudeApiKey: Flow<String?> = _claudeApiKey.asStateFlow()

    suspend fun saveClaudeApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_CLAUDE_API_KEY, apiKey).apply()
        _claudeApiKey.value = apiKey
    }

    fun getClaudeApiKeySync(): String? {
        return sharedPreferences.getString(KEY_CLAUDE_API_KEY, null)
    }

    suspend fun clearClaudeApiKey() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_CLAUDE_API_KEY).apply()
        _claudeApiKey.value = null
    }

    fun hasClaudeApiKey(): Boolean {
        return getClaudeApiKeySync()?.isNotBlank() == true
    }

    // Notification preference management
    private val _notificationsEnabled = MutableStateFlow(getNotificationsEnabledSync())
    val notificationsEnabled: Flow<Boolean> = _notificationsEnabled.asStateFlow()

    suspend fun setNotificationsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    fun getNotificationsEnabledSync(): Boolean {
        return sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    }

    // Dynamic notification preference management
    private val _dynamicNotificationsEnabled = MutableStateFlow(getDynamicNotificationsEnabledSync())
    val dynamicNotificationsEnabled: Flow<Boolean> = _dynamicNotificationsEnabled.asStateFlow()

    suspend fun setDynamicNotificationsEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_DYNAMIC_NOTIFICATIONS_ENABLED, enabled).apply()
        _dynamicNotificationsEnabled.value = enabled
    }

    fun getDynamicNotificationsEnabledSync(): Boolean {
        return sharedPreferences.getBoolean(KEY_DYNAMIC_NOTIFICATIONS_ENABLED, false)
    }

    // Notification time preference management
    private val _reminderHour = MutableStateFlow(getReminderHourSync())
    val reminderHour: Flow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(getReminderMinuteSync())
    val reminderMinute: Flow<Int> = _reminderMinute.asStateFlow()

    suspend fun setReminderTime(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()
        _reminderHour.value = hour
        _reminderMinute.value = minute
    }

    fun getReminderHourSync(): Int {
        return sharedPreferences.getInt(KEY_REMINDER_HOUR, DEFAULT_REMINDER_HOUR)
    }

    fun getReminderMinuteSync(): Int {
        return sharedPreferences.getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().clear().apply()
        _isLoggedIn.value = false
        _currentUserId.value = null
        _claudeApiKey.value = null
        _geminiApiKey.value = null
        _voyageApiKey.value = null
        _elevenLabsApiKey.value = null
        _deepgramApiKey.value = null
        _notificationsEnabled.value = false
        _dynamicNotificationsEnabled.value = false
        _reminderHour.value = DEFAULT_REMINDER_HOUR
        _reminderMinute.value = DEFAULT_REMINDER_MINUTE
        _autoDailyToolEnabled.value = false
        _dailyToolHour.value = DEFAULT_DAILY_TOOL_HOUR
        _dailyToolMinute.value = DEFAULT_DAILY_TOOL_MINUTE
        _scheduledCallEnabled.value = false
        _scheduledCallHour.value = DEFAULT_SCHEDULED_CALL_HOUR
        _scheduledCallMinute.value = DEFAULT_SCHEDULED_CALL_MINUTE
        _ragMigrationComplete.value = false
        _ragFallbackEnabled.value = true
        _ragAutoSyncEnabled.value = true
        _lastOverallSyncTimestamp.value = 0L
        _lastCheckedTimestamp.value = 0L
    }

    // Gemini API Key management
    private val _geminiApiKey = MutableStateFlow(getGeminiApiKeySync())
    val geminiApiKey: Flow<String?> = _geminiApiKey.asStateFlow()

    suspend fun saveGeminiApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
        _geminiApiKey.value = apiKey
    }

    fun getGeminiApiKeySync(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API_KEY, null)
    }

    suspend fun clearGeminiApiKey() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_GEMINI_API_KEY).apply()
        _geminiApiKey.value = null
    }

    fun hasGeminiApiKey(): Boolean {
        return getGeminiApiKeySync()?.isNotBlank() == true
    }

    // Voyage API Key management
    private val _voyageApiKey = MutableStateFlow(getVoyageApiKeySync())
    val voyageApiKey: Flow<String?> = _voyageApiKey.asStateFlow()

    suspend fun saveVoyageApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_VOYAGE_API_KEY, apiKey).apply()
        _voyageApiKey.value = apiKey
    }

    fun getVoyageApiKeySync(): String? {
        return sharedPreferences.getString(KEY_VOYAGE_API_KEY, null)
    }

    suspend fun clearVoyageApiKey() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_VOYAGE_API_KEY).apply()
        _voyageApiKey.value = null
    }

    fun hasVoyageApiKey(): Boolean {
        return getVoyageApiKeySync()?.isNotBlank() == true
    }

    // RAG Migration state
    private val _ragMigrationComplete = MutableStateFlow(getRagMigrationCompleteSync())
    val ragMigrationComplete: Flow<Boolean> = _ragMigrationComplete.asStateFlow()

    suspend fun setRagMigrationComplete(complete: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_RAG_MIGRATION_COMPLETE, complete).apply()
        _ragMigrationComplete.value = complete
    }

    fun getRagMigrationCompleteSync(): Boolean {
        return sharedPreferences.getBoolean(KEY_RAG_MIGRATION_COMPLETE, false)
    }

    // RAG Fallback preference - when disabled, prefer errors over fallback to traditional context
    private val _ragFallbackEnabled = MutableStateFlow(getRagFallbackEnabledSync())
    val ragFallbackEnabled: Flow<Boolean> = _ragFallbackEnabled.asStateFlow()

    suspend fun setRagFallbackEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_RAG_FALLBACK_ENABLED, enabled).apply()
        _ragFallbackEnabled.value = enabled
    }

    fun getRagFallbackEnabledSync(): Boolean {
        // Default to true for backward compatibility
        return sharedPreferences.getBoolean(KEY_RAG_FALLBACK_ENABLED, true)
    }

    // RAG Auto-Sync preference - when enabled, automatically sync Room changes to Kuzu
    private val _ragAutoSyncEnabled = MutableStateFlow(getRagAutoSyncEnabledSync())
    val ragAutoSyncEnabled: Flow<Boolean> = _ragAutoSyncEnabled.asStateFlow()

    suspend fun setRagAutoSyncEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_RAG_AUTO_SYNC_ENABLED, enabled).apply()
        _ragAutoSyncEnabled.value = enabled
    }

    fun getRagAutoSyncEnabledSync(): Boolean {
        // Default to true for new RAG users (auto-sync is the expected behavior)
        return sharedPreferences.getBoolean(KEY_RAG_AUTO_SYNC_ENABLED, true)
    }

    // Last sync timestamps for incremental syncing
    // These track when each entity type was last synced to Kuzu

    suspend fun setLastJournalSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_JOURNAL_SYNC, timestamp).apply()
    }

    fun getLastJournalSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_JOURNAL_SYNC, 0L)
    }

    suspend fun setLastMessageSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_MESSAGE_SYNC, timestamp).apply()
    }

    fun getLastMessageSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_MESSAGE_SYNC, 0L)
    }

    suspend fun setLastAgendaSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_AGENDA_SYNC, timestamp).apply()
    }

    fun getLastAgendaSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_AGENDA_SYNC, 0L)
    }

    suspend fun setLastSummarySyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_SUMMARY_SYNC, timestamp).apply()
    }

    fun getLastSummarySyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_SUMMARY_SYNC, 0L)
    }

    suspend fun setLastDailyAppSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_DAILY_APP_SYNC, timestamp).apply()
    }

    fun getLastDailyAppSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_DAILY_APP_SYNC, 0L)
    }

    suspend fun setLastNoteSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_NOTE_SYNC, timestamp).apply()
    }

    fun getLastNoteSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_NOTE_SYNC, 0L)
    }

    suspend fun setLastUserGoalSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_USER_GOAL_SYNC, timestamp).apply()
    }

    fun getLastUserGoalSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_USER_GOAL_SYNC, 0L)
    }

    suspend fun setLastUserTaskSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_USER_TASK_SYNC, timestamp).apply()
    }

    fun getLastUserTaskSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_USER_TASK_SYNC, 0L)
    }

    // Overall last sync timestamp (updated when any actual data is synced)
    private val _lastOverallSyncTimestamp = MutableStateFlow(getLastOverallSyncTimestampSync())
    val lastOverallSyncTimestamp: Flow<Long> = _lastOverallSyncTimestamp.asStateFlow()

    suspend fun setLastOverallSyncTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_OVERALL_SYNC, timestamp).apply()
        _lastOverallSyncTimestamp.value = timestamp
    }

    fun getLastOverallSyncTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_OVERALL_SYNC, 0L)
    }

    // Last checked timestamp (updated every time sync checks for changes, even if none found)
    private val _lastCheckedTimestamp = MutableStateFlow(getLastCheckedTimestampSync())
    val lastCheckedTimestamp: Flow<Long> = _lastCheckedTimestamp.asStateFlow()

    suspend fun setLastCheckedTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putLong(KEY_LAST_CHECKED, timestamp).apply()
        _lastCheckedTimestamp.value = timestamp
    }

    fun getLastCheckedTimestampSync(): Long {
        return sharedPreferences.getLong(KEY_LAST_CHECKED, 0L)
    }

    // Clear all sync timestamps (used when resetting RAG database)
    suspend fun clearAllSyncTimestamps() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove(KEY_LAST_JOURNAL_SYNC)
            .remove(KEY_LAST_MESSAGE_SYNC)
            .remove(KEY_LAST_AGENDA_SYNC)
            .remove(KEY_LAST_SUMMARY_SYNC)
            .remove(KEY_LAST_DAILY_APP_SYNC)
            .remove(KEY_LAST_NOTE_SYNC)
            .remove(KEY_LAST_USER_GOAL_SYNC)
            .remove(KEY_LAST_USER_TASK_SYNC)
            .remove(KEY_LAST_OVERALL_SYNC)
            .remove(KEY_LAST_CHECKED)
            .apply()
        _lastOverallSyncTimestamp.value = 0L
        _lastCheckedTimestamp.value = 0L
    }

    /**
     * Waits for the userId to be available, with retry logic.
     * This utility method prevents code duplication across ViewModels.
     *
     * @param maxAttempts Maximum number of retry attempts (default: 10)
     * @param delayMs Delay between attempts in milliseconds (default: 100)
     * @return The userId if available, null if not found after all attempts
     */
    suspend fun awaitUserId(maxAttempts: Int = 10, delayMs: Long = 100): String? {
        var attempts = 0
        while (attempts < maxAttempts) {
            currentUserId.first()?.let { return it }
            delay(delayMs)
            attempts++
        }
        return null
    }

    // Automatic Daily Tool generation preference management
    private val _autoDailyToolEnabled = MutableStateFlow(getAutoDailyToolEnabledSync())
    val autoDailyToolEnabled: Flow<Boolean> = _autoDailyToolEnabled.asStateFlow()

    private val _dailyToolHour = MutableStateFlow(getDailyToolHourSync())
    val dailyToolHour: Flow<Int> = _dailyToolHour.asStateFlow()

    private val _dailyToolMinute = MutableStateFlow(getDailyToolMinuteSync())
    val dailyToolMinute: Flow<Int> = _dailyToolMinute.asStateFlow()

    suspend fun setAutoDailyToolEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_DAILY_TOOL_ENABLED, enabled).apply()
        _autoDailyToolEnabled.value = enabled
    }

    fun getAutoDailyToolEnabledSync(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_DAILY_TOOL_ENABLED, false)
    }

    suspend fun setDailyToolTime(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putInt(KEY_DAILY_TOOL_HOUR, hour)
            .putInt(KEY_DAILY_TOOL_MINUTE, minute)
            .apply()
        _dailyToolHour.value = hour
        _dailyToolMinute.value = minute
    }

    fun getDailyToolHourSync(): Int {
        return sharedPreferences.getInt(KEY_DAILY_TOOL_HOUR, DEFAULT_DAILY_TOOL_HOUR)
    }

    fun getDailyToolMinuteSync(): Int {
        return sharedPreferences.getInt(KEY_DAILY_TOOL_MINUTE, DEFAULT_DAILY_TOOL_MINUTE)
    }

    // Scheduled coach call preference management
    private val _scheduledCallEnabled = MutableStateFlow(getScheduledCallEnabledSync())
    val scheduledCallEnabled: Flow<Boolean> = _scheduledCallEnabled.asStateFlow()

    private val _scheduledCallHour = MutableStateFlow(getScheduledCallHourSync())
    val scheduledCallHour: Flow<Int> = _scheduledCallHour.asStateFlow()

    private val _scheduledCallMinute = MutableStateFlow(getScheduledCallMinuteSync())
    val scheduledCallMinute: Flow<Int> = _scheduledCallMinute.asStateFlow()

    suspend fun setScheduledCallEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_SCHEDULED_CALL_ENABLED, enabled).apply()
        _scheduledCallEnabled.value = enabled
    }

    fun getScheduledCallEnabledSync(): Boolean {
        return sharedPreferences.getBoolean(KEY_SCHEDULED_CALL_ENABLED, false)
    }

    suspend fun setScheduledCallTime(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putInt(KEY_SCHEDULED_CALL_HOUR, hour)
            .putInt(KEY_SCHEDULED_CALL_MINUTE, minute)
            .apply()
        _scheduledCallHour.value = hour
        _scheduledCallMinute.value = minute
    }

    fun getScheduledCallHourSync(): Int {
        return sharedPreferences.getInt(KEY_SCHEDULED_CALL_HOUR, DEFAULT_SCHEDULED_CALL_HOUR)
    }

    fun getScheduledCallMinuteSync(): Int {
        return sharedPreferences.getInt(KEY_SCHEDULED_CALL_MINUTE, DEFAULT_SCHEDULED_CALL_MINUTE)
    }

    // ElevenLabs API Key management
    private val _elevenLabsApiKey = MutableStateFlow(getElevenLabsApiKeySync())
    val elevenLabsApiKey: Flow<String?> = _elevenLabsApiKey.asStateFlow()

    suspend fun saveElevenLabsApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_ELEVENLABS_API_KEY, apiKey).apply()
        _elevenLabsApiKey.value = apiKey
    }

    fun getElevenLabsApiKeySync(): String? {
        return sharedPreferences.getString(KEY_ELEVENLABS_API_KEY, null)
    }

    suspend fun clearElevenLabsApiKey() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_ELEVENLABS_API_KEY).apply()
        _elevenLabsApiKey.value = null
    }

    fun hasElevenLabsApiKey(): Boolean {
        return getElevenLabsApiKeySync()?.isNotBlank() == true
    }

    // Deepgram API Key management
    private val _deepgramApiKey = MutableStateFlow(getDeepgramApiKeySync())
    val deepgramApiKey: Flow<String?> = _deepgramApiKey.asStateFlow()

    suspend fun saveDeepgramApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_DEEPGRAM_API_KEY, apiKey).apply()
        _deepgramApiKey.value = apiKey
    }

    fun getDeepgramApiKeySync(): String? {
        return sharedPreferences.getString(KEY_DEEPGRAM_API_KEY, null)
    }

    suspend fun clearDeepgramApiKey() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_DEEPGRAM_API_KEY).apply()
        _deepgramApiKey.value = null
    }

    fun hasDeepgramApiKey(): Boolean {
        return getDeepgramApiKeySync()?.isNotBlank() == true
    }

    companion object {
        private const val PREFS_NAME = "encrypted_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_VOYAGE_API_KEY = "voyage_api_key"
        private const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
        private const val KEY_DEEPGRAM_API_KEY = "deepgram_api_key"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DYNAMIC_NOTIFICATIONS_ENABLED = "dynamic_notifications_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_AUTO_DAILY_TOOL_ENABLED = "auto_daily_tool_enabled"
        private const val KEY_DAILY_TOOL_HOUR = "daily_tool_hour"
        private const val KEY_DAILY_TOOL_MINUTE = "daily_tool_minute"
        private const val KEY_RAG_MIGRATION_COMPLETE = "rag_migration_complete"
        private const val KEY_RAG_FALLBACK_ENABLED = "rag_fallback_enabled"
        private const val KEY_RAG_AUTO_SYNC_ENABLED = "rag_auto_sync_enabled"
        private const val KEY_SCHEDULED_CALL_ENABLED = "scheduled_call_enabled"
        private const val KEY_SCHEDULED_CALL_HOUR = "scheduled_call_hour"
        private const val KEY_SCHEDULED_CALL_MINUTE = "scheduled_call_minute"
        private const val KEY_LAST_JOURNAL_SYNC = "last_journal_sync"
        private const val KEY_LAST_MESSAGE_SYNC = "last_message_sync"
        private const val KEY_LAST_AGENDA_SYNC = "last_agenda_sync"
        private const val KEY_LAST_SUMMARY_SYNC = "last_summary_sync"
        private const val KEY_LAST_DAILY_APP_SYNC = "last_daily_app_sync"
        private const val KEY_LAST_NOTE_SYNC = "last_note_sync"
        private const val KEY_LAST_USER_GOAL_SYNC = "last_user_goal_sync"
        private const val KEY_LAST_USER_TASK_SYNC = "last_user_task_sync"
        private const val KEY_LAST_OVERALL_SYNC = "last_overall_sync"
        private const val KEY_LAST_CHECKED = "last_checked"
        const val DEFAULT_REMINDER_HOUR = 22
        const val DEFAULT_REMINDER_MINUTE = 15
        const val DEFAULT_DAILY_TOOL_HOUR = 8
        const val DEFAULT_DAILY_TOOL_MINUTE = 0
        const val DEFAULT_SCHEDULED_CALL_HOUR = 21
        const val DEFAULT_SCHEDULED_CALL_MINUTE = 0
    }
}
