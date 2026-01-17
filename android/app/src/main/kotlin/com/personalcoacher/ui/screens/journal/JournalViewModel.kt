package com.personalcoacher.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.EventSuggestionDao
import com.personalcoacher.data.local.entity.EventSuggestionEntity
import com.personalcoacher.data.remote.EventAnalysisResult
import com.personalcoacher.data.remote.EventAnalysisService
import com.personalcoacher.data.remote.dto.EventSuggestionDto
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.domain.model.EventSuggestionStatus
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.notification.EventAnalysisWorker
import com.personalcoacher.util.DateUtils
import com.personalcoacher.util.DebugLogHelper
import com.personalcoacher.util.Resource
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedEntry: JournalEntry? = null,
    val showEditor: Boolean = false,
    val editorState: JournalEditorState = JournalEditorState(),
    val processingEntryIds: Set<String> = emptySet(), // Entries currently being analyzed
    val showDebugDialog: Boolean = false,
    val debugLogs: String = ""
)

data class JournalEditorState(
    val content: String = "",
    val mood: Mood? = null,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val isSaving: Boolean = false,
    val editingEntryId: String? = null
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val tokenManager: TokenManager,
    private val workManager: WorkManager,
    private val debugLog: DebugLogHelper,
    private val eventAnalysisService: EventAnalysisService,
    private val eventSuggestionDao: EventSuggestionDao
) : ViewModel() {

    companion object {
        private const val TAG = "JournalViewModel"
    }

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadEntries()
    }

    private fun loadEntries() {
        viewModelScope.launch {
            // Wait for userId to be available with a retry mechanism
            var userId: String? = null
            var attempts = 0
            while (userId == null && attempts < 10) {
                userId = tokenManager.currentUserId.first()
                if (userId == null) {
                    kotlinx.coroutines.delay(100) // Wait a bit and retry
                    attempts++
                }
            }

            currentUserId = userId
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "Unable to load user") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            journalRepository.getEntries(userId).collect { entries ->
                _uiState.update {
                    it.copy(
                        entries = entries,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            _uiState.update { it.copy(isRefreshing = true) }

            when (val result = journalRepository.syncEntries(userId)) {
                is Resource.Error -> {
                    _uiState.update { it.copy(isRefreshing = false, error = result.message) }
                }
                else -> {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun openNewEntry() {
        _uiState.update {
            it.copy(
                showEditor = true,
                editorState = JournalEditorState()
            )
        }
    }

    fun openEditEntry(entry: JournalEntry) {
        _uiState.update {
            it.copy(
                showEditor = true,
                selectedEntry = entry,
                editorState = JournalEditorState(
                    content = entry.content,
                    mood = entry.mood,
                    tags = entry.tags,
                    editingEntryId = entry.id
                )
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                showEditor = false,
                selectedEntry = null,
                editorState = JournalEditorState()
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(content = content))
        }
    }

    fun updateMood(mood: Mood?) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(mood = mood))
        }
    }

    fun updateTagInput(input: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(tagInput = input))
        }
    }

    fun addTag() {
        val currentState = _uiState.value.editorState
        val newTag = currentState.tagInput.trim()
        if (newTag.isNotEmpty() && !currentState.tags.contains(newTag)) {
            _uiState.update {
                it.copy(
                    editorState = it.editorState.copy(
                        tags = it.editorState.tags + newTag,
                        tagInput = ""
                    )
                )
            }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(
                    tags = it.editorState.tags - tag
                )
            )
        }
    }

    fun saveEntry() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val editorState = _uiState.value.editorState

            if (editorState.content.isBlank()) {
                _uiState.update { it.copy(error = "Please write something before saving") }
                return@launch
            }

            _uiState.update {
                it.copy(editorState = it.editorState.copy(isSaving = true))
            }

            val result = if (editorState.editingEntryId != null) {
                journalRepository.updateEntry(
                    id = editorState.editingEntryId,
                    content = editorState.content,
                    mood = editorState.mood,
                    tags = editorState.tags
                )
            } else {
                journalRepository.createEntry(
                    userId = userId,
                    content = editorState.content,
                    mood = editorState.mood,
                    tags = editorState.tags,
                    date = Instant.now()
                )
            }

            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            showEditor = false,
                            selectedEntry = null,
                            editorState = JournalEditorState()
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.message,
                            editorState = it.editorState.copy(isSaving = false)
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteEntry(entry: JournalEntry) {
        viewModelScope.launch {
            journalRepository.deleteEntry(entry.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Manually trigger event analysis for a journal entry.
     * This is useful when automatic analysis fails or user wants to re-analyze.
     */
    fun analyzeEntryForEvents(entry: JournalEntry) {
        val userId = currentUserId ?: return

        // Remove HTML tags for analysis
        val plainTextContent = entry.content.replace(Regex("<[^>]*>"), " ").trim()

        // Convert the entry's date to LocalDate for proper relative date handling
        val entryDate = DateUtils.instantToLocalDate(entry.date)

        debugLog.log(TAG, "Manual analyzeEntryForEvents called - userId=$userId, entryId=${entry.id}, entryDate=$entryDate, contentLength=${plainTextContent.length}")

        // Only analyze if content is substantial
        if (plainTextContent.length < 20) {
            debugLog.log(TAG, "Content too short (${plainTextContent.length} chars), skipping analysis")
            _uiState.update { it.copy(error = "Entry content is too short to analyze for events") }
            return
        }

        // Mark entry as processing
        _uiState.update { it.copy(processingEntryIds = it.processingEntryIds + entry.id) }

        val inputData = workDataOf(
            EventAnalysisWorker.KEY_USER_ID to userId,
            EventAnalysisWorker.KEY_JOURNAL_ENTRY_ID to entry.id,
            EventAnalysisWorker.KEY_JOURNAL_CONTENT to plainTextContent,
            EventAnalysisWorker.KEY_JOURNAL_ENTRY_DATE to entryDate.toString()
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<EventAnalysisWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        // Use unique work name to avoid duplicate analysis for the same entry
        val workName = "${EventAnalysisWorker.WORK_NAME_PREFIX}${entry.id}"
        debugLog.log(TAG, "Enqueuing EventAnalysisWorker with expedited policy, workName=$workName")

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Observe work status to update UI
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData(workName).observeForever { workInfos ->
                workInfos.firstOrNull()?.let { workInfo ->
                    debugLog.log(TAG, "WorkInfo state for ${entry.id}: ${workInfo.state}")
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            // Remove from processing set
                            _uiState.update { it.copy(processingEntryIds = it.processingEntryIds - entry.id) }
                        }
                        else -> { /* Still processing */ }
                    }
                }
            }
        }
    }

    /**
     * Check if a specific entry is currently being analyzed
     */
    fun isEntryProcessing(entryId: String): Boolean {
        return _uiState.value.processingEntryIds.contains(entryId)
    }

    /**
     * Debug analyze: Performs event analysis directly (not via WorkManager)
     * and displays logs in a dialog so users can see what's happening.
     * Uses local Claude API via EventAnalysisService.
     */
    fun debugAnalyzeEntryForEvents(entry: JournalEntry) {
        val userId = currentUserId ?: run {
            showDebugResult("Error: User ID not available")
            return
        }

        // Remove HTML tags for analysis
        val plainTextContent = entry.content.replace(Regex("<[^>]*>"), " ").trim()

        // Convert the entry's date to LocalDate for proper relative date handling
        val entryDate = DateUtils.instantToLocalDate(entry.date)

        val logs = StringBuilder()
        logs.appendLine("=== DEBUG EVENT ANALYSIS (Local Claude API) ===")
        logs.appendLine("Time: ${java.time.LocalDateTime.now()}")
        logs.appendLine("User ID: $userId")
        logs.appendLine("Entry ID: ${entry.id}")
        logs.appendLine("Entry Date: $entryDate")
        logs.appendLine("Content length: ${plainTextContent.length} chars")
        logs.appendLine("Content preview: ${plainTextContent.take(100)}...")
        logs.appendLine("")

        if (plainTextContent.length < 20) {
            logs.appendLine("ERROR: Content too short (min 20 chars)")
            showDebugResult(logs.toString())
            return
        }

        // Mark entry as processing
        _uiState.update { it.copy(processingEntryIds = it.processingEntryIds + entry.id) }
        logs.appendLine("Starting local Claude API call with entryDate=$entryDate...")

        viewModelScope.launch {
            try {
                logs.appendLine("Calling EventAnalysisService.analyzeJournalEntry(content, entryDate=$entryDate)...")

                when (val result = eventAnalysisService.analyzeJournalEntry(plainTextContent, entryDate)) {
                    is EventAnalysisResult.Success -> {
                        val suggestions = result.suggestions

                        logs.appendLine("Response successful!")
                        logs.appendLine("Found ${suggestions.size} event suggestions")
                        logs.appendLine("")

                        if (suggestions.isNotEmpty()) {
                            suggestions.forEachIndexed { index, dto ->
                                logs.appendLine("--- Suggestion ${index + 1} ---")
                                logs.appendLine("Title: ${dto.title}")
                                logs.appendLine("Description: ${dto.description ?: "N/A"}")
                                logs.appendLine("Start: ${dto.startTime}")
                                logs.appendLine("End: ${dto.endTime ?: "N/A"}")
                                logs.appendLine("All day: ${dto.isAllDay}")
                                logs.appendLine("Location: ${dto.location ?: "N/A"}")
                                logs.appendLine("")
                            }

                            // Save suggestions to database
                            logs.appendLine("Saving suggestions to database...")
                            val savedSuggestions = suggestions.map { dto ->
                                EventSuggestion(
                                    id = UUID.randomUUID().toString(),
                                    userId = userId,
                                    journalEntryId = entry.id,
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
                            logs.appendLine("Saved ${savedSuggestions.size} suggestions to database")
                            logs.appendLine("")
                            logs.appendLine("SUCCESS: Check home screen for suggestions!")
                        } else {
                            logs.appendLine("No events detected in this entry.")
                            logs.appendLine("Try writing about specific events with dates/times.")
                        }
                    }

                    is EventAnalysisResult.Error -> {
                        logs.appendLine("API ERROR:")
                        logs.appendLine("Message: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                logs.appendLine("EXCEPTION: ${e.javaClass.simpleName}")
                logs.appendLine("Message: ${e.message}")
                logs.appendLine("")
                logs.appendLine("Stack trace:")
                logs.appendLine(e.stackTraceToString().take(500))
            } finally {
                // Remove from processing set
                _uiState.update { it.copy(processingEntryIds = it.processingEntryIds - entry.id) }
                showDebugResult(logs.toString())
            }
        }
    }

    private fun showDebugResult(logs: String) {
        _uiState.update { it.copy(showDebugDialog = true, debugLogs = logs) }
    }

    fun dismissDebugDialog() {
        _uiState.update { it.copy(showDebugDialog = false, debugLogs = "") }
    }
}
