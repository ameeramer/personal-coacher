package com.personalcoacher.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        _notificationsEnabled.value = false
        _dynamicNotificationsEnabled.value = false
        _reminderHour.value = DEFAULT_REMINDER_HOUR
        _reminderMinute.value = DEFAULT_REMINDER_MINUTE
    }

    companion object {
        private const val PREFS_NAME = "encrypted_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DYNAMIC_NOTIFICATIONS_ENABLED = "dynamic_notifications_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val DEFAULT_REMINDER_HOUR = 22
        const val DEFAULT_REMINDER_MINUTE = 15
    }
}
