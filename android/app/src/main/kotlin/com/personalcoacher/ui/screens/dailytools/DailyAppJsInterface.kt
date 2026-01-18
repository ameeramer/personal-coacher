package com.personalcoacher.ui.screens.dailytools

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.personalcoacher.domain.repository.DailyAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * JavaScript interface that bridges the AI-generated web app with native Android functionality.
 * This enables schema-less data persistence for any data structure the app needs.
 *
 * Available in JavaScript as `Android.methodName()`
 *
 * Note: For methods that need to return data asynchronously (loadData, getAllData, hasData),
 * we use a callback-based approach to avoid blocking the main thread. The JavaScript side
 * should use the async versions: loadDataAsync, getAllDataAsync, hasDataAsync.
 */
class DailyAppJsInterface(
    private val appId: String,
    private val appTitle: String,
    private val createdAt: Long,
    private val repository: DailyAppRepository,
    webView: WebView? = null
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val webViewRef = WeakReference(webView)

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
     * Load a previously saved value asynchronously.
     * Calls the JavaScript callback function with the result.
     *
     * @param key The key to retrieve
     * @param callbackName The name of the JavaScript function to call with the result
     */
    @JavascriptInterface
    fun loadDataAsync(key: String, callbackName: String) {
        scope.launch {
            val value = repository.getDataByKey(appId, key) ?: ""
            callJavaScript("$callbackName('${escapeForJs(value)}')")
        }
    }

    /**
     * Load a previously saved value synchronously.
     * WARNING: This method blocks the calling thread. For better performance,
     * prefer using loadDataAsync with a callback.
     *
     * @param key The key to retrieve
     * @return The stored value or empty string
     */
    @JavascriptInterface
    fun loadData(key: String): String {
        // Use a CompletableFuture to avoid runBlocking on the main thread
        val future = java.util.concurrent.CompletableFuture<String>()
        scope.launch {
            val value = repository.getDataByKey(appId, key) ?: ""
            future.complete(value)
        }
        return try {
            // Wait with timeout to prevent ANRs (5 second timeout)
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            android.util.Log.w("DailyAppJsInterface", "loadData timed out or failed: ${e.message}")
            ""
        }
    }

    /**
     * Get all saved data for this app as a JSON object asynchronously.
     * Calls the JavaScript callback function with the result.
     *
     * @param callbackName The name of the JavaScript function to call with the result
     */
    @JavascriptInterface
    fun getAllDataAsync(callbackName: String) {
        scope.launch {
            val data = repository.getAllDataAsMap(appId)
            val json = gson.toJson(data)
            callJavaScript("$callbackName($json)")
        }
    }

    /**
     * Get all saved data for this app as a JSON object synchronously.
     * WARNING: This method blocks the calling thread. For better performance,
     * prefer using getAllDataAsync with a callback.
     *
     * @return JSON string of all key-value pairs: {"key1": "value1", "key2": "value2"}
     */
    @JavascriptInterface
    fun getAllData(): String {
        val future = java.util.concurrent.CompletableFuture<String>()
        scope.launch {
            val data = repository.getAllDataAsMap(appId)
            future.complete(gson.toJson(data))
        }
        return try {
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            android.util.Log.w("DailyAppJsInterface", "getAllData timed out or failed: ${e.message}")
            "{}"
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
     * Check if a specific key exists in storage asynchronously.
     * Calls the JavaScript callback function with the result.
     *
     * @param key The key to check
     * @param callbackName The name of the JavaScript function to call with the result
     */
    @JavascriptInterface
    fun hasDataAsync(key: String, callbackName: String) {
        scope.launch {
            val value = repository.getDataByKey(appId, key)
            val exists = (value != null).toString()
            callJavaScript("$callbackName($exists)")
        }
    }

    /**
     * Check if a specific key exists in storage synchronously.
     * WARNING: This method blocks the calling thread. For better performance,
     * prefer using hasDataAsync with a callback.
     *
     * @param key The key to check
     * @return "true" if exists, "false" otherwise (as string for JavaScript compatibility)
     */
    @JavascriptInterface
    fun hasData(key: String): String {
        val future = java.util.concurrent.CompletableFuture<String>()
        scope.launch {
            val value = repository.getDataByKey(appId, key)
            future.complete((value != null).toString())
        }
        return try {
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            android.util.Log.w("DailyAppJsInterface", "hasData timed out or failed: ${e.message}")
            "false"
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

    /**
     * Helper function to execute JavaScript on the WebView from the main thread.
     */
    private fun callJavaScript(script: String) {
        mainHandler.post {
            webViewRef.get()?.evaluateJavascript(script, null)
        }
    }

    /**
     * Escape a string for safe use in JavaScript single-quoted strings.
     */
    private fun escapeForJs(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
