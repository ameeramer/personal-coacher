package com.personalcoacher.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalcoacher.data.local.dao.EventSuggestionDao
import com.personalcoacher.data.local.entity.EventSuggestionEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.AnalyzeJournalRequest
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.domain.model.EventSuggestionStatus
import com.personalcoacher.util.DebugLogHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Background worker that analyzes journal entries for potential calendar events using AI.
 *
 * This worker is triggered when a user saves a journal entry and allows the AI analysis
 * to continue even if the user leaves the app. When events are detected, a notification
 * is sent to alert the user about the new suggestions.
 *
 * Flow:
 * 1. User saves journal entry -> JournalEditorViewModel enqueues this worker
 * 2. Worker calls the analyze API endpoint
 * 3. If events are detected, they're saved locally as suggestions
 * 4. A notification is sent to inform the user about new suggestions
 * 5. User can accept/reject suggestions from the home screen
 */
@HiltWorker
class EventAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: PersonalCoachApi,
    private val eventSuggestionDao: EventSuggestionDao,
    private val notificationHelper: NotificationHelper,
    private val debugLog: DebugLogHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PREFIX = "event_analysis_"
        const val KEY_USER_ID = "user_id"
        const val KEY_JOURNAL_ENTRY_ID = "journal_entry_id"
        const val KEY_JOURNAL_CONTENT = "journal_content"
        private const val TAG = "EventAnalysisWorker"
        private const val DNS_TIMEOUT_MS = 5000L
    }

    /**
     * Pre-flight check to verify DNS resolution is working.
     */
    @androidx.annotation.WorkerThread
    private fun isDnsResolutionWorking(): Boolean {
        return try {
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit<Boolean> {
                try {
                    val addresses = InetAddress.getAllByName("personal-coacher.vercel.app")
                    addresses.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
            future.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            debugLog.log(TAG, "DNS pre-flight check timed out after ${DNS_TIMEOUT_MS}ms")
            false
        } catch (e: Exception) {
            debugLog.log(TAG, "DNS pre-flight check failed: ${e.message}")
            false
        }
    }

    override suspend fun doWork(): Result {
        debugLog.log(TAG, "=== EventAnalysisWorker STARTED ===")

        val userId = inputData.getString(KEY_USER_ID)
        val journalEntryId = inputData.getString(KEY_JOURNAL_ENTRY_ID)
        val journalContent = inputData.getString(KEY_JOURNAL_CONTENT)

        debugLog.log(TAG, "doWork() called - userId=$userId, journalEntryId=$journalEntryId, contentLength=${journalContent?.length}")

        if (userId.isNullOrBlank() || journalEntryId.isNullOrBlank() || journalContent.isNullOrBlank()) {
            debugLog.log(TAG, "Missing required parameters, aborting")
            return Result.failure()
        }

        // Pre-flight DNS check
        debugLog.log(TAG, "Checking DNS resolution...")
        if (!isDnsResolutionWorking()) {
            debugLog.log(TAG, "DNS resolution not working, will retry later")
            return Result.retry()
        }
        debugLog.log(TAG, "DNS check passed")

        return try {
            debugLog.log(TAG, "Calling analyze API for journal entry: $journalEntryId")

            val response = api.analyzeJournalForEvents(
                AnalyzeJournalRequest(
                    journalEntryId = journalEntryId,
                    content = journalContent
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val apiResponse = response.body()!!
                val suggestions = apiResponse.suggestions

                debugLog.log(TAG, "API response received, found ${suggestions.size} event suggestions")

                if (suggestions.isNotEmpty()) {
                    // Save suggestions to local database
                    val savedSuggestions = suggestions.map { dto ->
                        EventSuggestion(
                            id = UUID.randomUUID().toString(),
                            userId = userId,
                            journalEntryId = journalEntryId,
                            title = dto.title,
                            description = dto.description,
                            suggestedStartTime = Instant.parse(dto.startTime),
                            suggestedEndTime = dto.endTime?.let { Instant.parse(it) },
                            isAllDay = dto.isAllDay,
                            location = dto.location,
                            status = EventSuggestionStatus.PENDING,
                            createdAt = Instant.now(),
                            processedAt = null
                        )
                    }

                    savedSuggestions.forEach { suggestion ->
                        eventSuggestionDao.insertSuggestion(
                            EventSuggestionEntity.fromDomainModel(suggestion)
                        )
                    }

                    debugLog.log(TAG, "Saved ${savedSuggestions.size} suggestions to database")

                    // Send notification about new suggestions
                    val notificationTitle = if (savedSuggestions.size == 1) {
                        "New event suggestion"
                    } else {
                        "${savedSuggestions.size} new event suggestions"
                    }

                    val notificationBody = if (savedSuggestions.size == 1) {
                        "\"${savedSuggestions.first().title}\" - tap to add to your agenda"
                    } else {
                        savedSuggestions.take(2).joinToString(", ") { "\"${it.title}\"" } +
                                if (savedSuggestions.size > 2) " and more" else ""
                    }

                    debugLog.log(TAG, "Sending notification: $notificationTitle - $notificationBody")
                    val notifResult = notificationHelper.showEventSuggestionNotification(
                        title = notificationTitle,
                        body = notificationBody,
                        suggestionCount = savedSuggestions.size
                    )
                    debugLog.log(TAG, "Notification result: $notifResult")
                } else {
                    debugLog.log(TAG, "No events detected in journal entry")
                }

                debugLog.log(TAG, "doWork() returning Result.success()")
                Result.success()
            } else {
                val errorBody = response.errorBody()?.string()
                debugLog.log(TAG, "API error: ${response.code()} - $errorBody")

                // Don't retry on 4xx errors (client errors)
                if (response.code() in 400..499) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            debugLog.log(TAG, "doWork() EXCEPTION: ${e.message}")

            // Check if this is a network error that should be retried
            val isNetworkError = e.message?.contains("UnknownHostException", ignoreCase = true) == true ||
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    e.message?.contains("No address associated", ignoreCase = true) == true ||
                    e.message?.contains("SocketTimeoutException", ignoreCase = true) == true ||
                    e.message?.contains("ConnectException", ignoreCase = true) == true ||
                    e.message?.contains("Network", ignoreCase = true) == true ||
                    e.message?.contains("SocketException", ignoreCase = true) == true ||
                    e is java.net.SocketException

            if (isNetworkError) {
                debugLog.log(TAG, "Network error detected, returning Result.retry()")
                return Result.retry()
            }

            Result.failure()
        }
    }
}
