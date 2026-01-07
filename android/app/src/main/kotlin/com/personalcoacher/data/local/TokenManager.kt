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

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().clear().apply()
        _isLoggedIn.value = false
        _currentUserId.value = null
    }

    companion object {
        private const val PREFS_NAME = "encrypted_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
    }
}
