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
        _notificationsEnabled.value = false
        _dynamicNotificationsEnabled.value = false
        _reminderHour.value = DEFAULT_REMINDER_HOUR
        _reminderMinute.value = DEFAULT_REMINDER_MINUTE
        _autoDailyToolEnabled.value = false
        _dailyToolHour.value = DEFAULT_DAILY_TOOL_HOUR
        _dailyToolMinute.value = DEFAULT_DAILY_TOOL_MINUTE
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

    companion object {
        private const val PREFS_NAME = "encrypted_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DYNAMIC_NOTIFICATIONS_ENABLED = "dynamic_notifications_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_AUTO_DAILY_TOOL_ENABLED = "auto_daily_tool_enabled"
        private const val KEY_DAILY_TOOL_HOUR = "daily_tool_hour"
        private const val KEY_DAILY_TOOL_MINUTE = "daily_tool_minute"
        const val DEFAULT_REMINDER_HOUR = 22
        const val DEFAULT_REMINDER_MINUTE = 15
        const val DEFAULT_DAILY_TOOL_HOUR = 8
        const val DEFAULT_DAILY_TOOL_MINUTE = 0
    }
}
