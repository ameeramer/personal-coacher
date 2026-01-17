package com.personalcoacher.ui.screens.dailytools

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.personalcoacher.domain.repository.DailyAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * JavaScript interface that bridges the AI-generated web app with native Android functionality.
 * This enables schema-less data persistence for any data structure the app needs.
 *
 * Available in JavaScript as `Android.methodName()`
 */
class DailyAppJsInterface(
    private val appId: String,
    private val appTitle: String,
    private val createdAt: Long,
    private val repository: DailyAppRepository
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Save a string value to persistent storage.
     * For complex data, use JSON.stringify() in JavaScript.
     *
     * @param key The key to store the value under
     * @param value The string value to store
     */
    @JavascriptInterface
    fun saveData(key: String, value: String) {
        scope.launch {
            repository.saveData(appId, key, value)
        }
    }

    /**
     * Load a previously saved value.
     * Returns empty string if not found.
     *
     * @param key The key to retrieve
     * @return The stored value or empty string
     */
    @JavascriptInterface
    fun loadData(key: String): String {
        return runBlocking {
            repository.getDataByKey(appId, key) ?: ""
        }
    }

    /**
     * Get all saved data for this app as a JSON object.
     * Useful for initializing app state on load.
     *
     * @return JSON string of all key-value pairs: {"key1": "value1", "key2": "value2"}
     */
    @JavascriptInterface
    fun getAllData(): String {
        return runBlocking {
            val data = repository.getAllDataAsMap(appId)
            gson.toJson(data)
        }
    }

    /**
     * Clear all saved data for this app.
     */
    @JavascriptInterface
    fun clearData() {
        scope.launch {
            repository.clearAllData(appId)
        }
    }

    /**
     * Get information about this app.
     *
     * @return JSON string with app metadata: {"title": "...", "createdAt": 123456789}
     */
    @JavascriptInterface
    fun getAppInfo(): String {
        return gson.toJson(
            mapOf(
                "title" to appTitle,
                "createdAt" to createdAt,
                "appId" to appId
            )
        )
    }

    /**
     * Log a message for debugging purposes.
     * Messages will appear in Android's logcat.
     *
     * @param message The message to log
     */
    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("DailyApp[$appId]", message)
    }

    /**
     * Check if a specific key exists in storage.
     *
     * @param key The key to check
     * @return "true" if exists, "false" otherwise (as string for JavaScript compatibility)
     */
    @JavascriptInterface
    fun hasData(key: String): String {
        return runBlocking {
            val value = repository.getDataByKey(appId, key)
            (value != null).toString()
        }
    }

    /**
     * Delete a specific key from storage.
     *
     * @param key The key to delete
     */
    @JavascriptInterface
    fun deleteData(key: String) {
        scope.launch {
            repository.deleteData(appId, key)
        }
    }
}
