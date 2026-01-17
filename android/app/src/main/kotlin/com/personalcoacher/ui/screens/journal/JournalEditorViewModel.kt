package com.personalcoacher.ui.screens.journal

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.notification.EventAnalysisWorker
import com.personalcoacher.util.DateUtils
import com.personalcoacher.util.DebugLogHelper
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EditorUiState(
    val content: String = "",
    val mood: Mood? = null,
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val selectedDate: LocalDate = DateUtils.getLogicalLocalDate(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val existingEntry: JournalEntry? = null
)

@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val workManager: WorkManager,
    private val tokenManager: TokenManager,
    private val debugLog: DebugLogHelper,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "JournalEditorViewModel"
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private val entryId: String? = savedStateHandle.get<String>("entryId")

    init {
        viewModelScope.launch {
            currentUserId = tokenManager.currentUserId.first()
            if (entryId != null) {
                loadEntry(entryId)
            }
        }
    }

    private fun loadEntry(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            journalRepository.getEntryById(id).collect { entry ->
                if (entry != null) {
                    _uiState.update {
                        it.copy(
                            content = entry.content,
                            mood = entry.mood,
                            tags = entry.tags,
                            selectedDate = DateUtils.instantToLocalDate(entry.date),
                            existingEntry = entry,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Entry not found") }
                }
            }
        }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    fun updateMood(mood: Mood?) {
        _uiState.update { it.copy(mood = mood) }
    }

    fun updateTagInput(input: String) {
        _uiState.update { it.copy(tagInput = input) }
    }

    fun updateSelectedDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun addTag() {
        val newTag = _uiState.value.tagInput.trim()
        if (newTag.isNotEmpty() && !_uiState.value.tags.contains(newTag)) {
            _uiState.update {
                it.copy(
                    tags = it.tags + newTag,
                    tagInput = ""
                )
            }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
    }

    fun saveEntry() {
        viewModelScope.launch {
            val state = _uiState.value
            val userId = currentUserId

            if (state.content.isBlank()) {
                _uiState.update { it.copy(error = "Please write something before saving") }
                return@launch
            }

            if (userId == null) {
                _uiState.update { it.copy(error = "User not logged in") }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true) }

            val selectedDateInstant = DateUtils.localDateToInstant(state.selectedDate)

            val result = if (state.existingEntry != null) {
                journalRepository.updateEntry(
                    id = state.existingEntry.id,
                    content = state.content,
                    mood = state.mood,
                    tags = state.tags,
                    date = selectedDateInstant
                )
            } else {
                journalRepository.createEntry(
                    userId = userId,
                    content = state.content,
                    mood = state.mood,
                    tags = state.tags,
                    date = selectedDateInstant
                )
            }

            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    // Analyze the journal entry for events in the background
                    // Pass the entry's date so relative dates like "tomorrow" are interpreted correctly
                    result.data?.let { savedEntry ->
                        analyzeEntryForEvents(userId, savedEntry.id, state.content, state.selectedDate)
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Schedules background analysis of the journal entry for potential calendar events.
     * Uses WorkManager to ensure the analysis continues even if the user leaves the app.
     * A notification will be sent when new event suggestions are detected.
     */
    private fun analyzeEntryForEvents(userId: String, journalEntryId: String, content: String, entryDate: LocalDate) {
        // Remove HTML tags for analysis
        val plainTextContent = content.replace(Regex("<[^>]*>"), " ").trim()

        debugLog.log(TAG, "analyzeEntryForEvents called - userId=$userId, entryId=$journalEntryId, entryDate=$entryDate, contentLength=${plainTextContent.length}")

        // Only analyze if content is substantial
        if (plainTextContent.length < 20) {
            debugLog.log(TAG, "Content too short (${plainTextContent.length} chars), skipping analysis")
            return
        }

        val inputData = workDataOf(
            EventAnalysisWorker.KEY_USER_ID to userId,
            EventAnalysisWorker.KEY_JOURNAL_ENTRY_ID to journalEntryId,
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
        val workName = "${EventAnalysisWorker.WORK_NAME_PREFIX}$journalEntryId"
        debugLog.log(TAG, "Enqueuing EventAnalysisWorker with expedited policy, workName=$workName")

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Observe work status for debugging using Flow (no memory leak)
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(workName).collect { workInfos ->
                workInfos.firstOrNull()?.let { workInfo ->
                    debugLog.log(TAG, "WorkInfo state: ${workInfo.state}, id=${workInfo.id}")
                    // Stop collecting once work is complete
                    if (workInfo.state.isFinished) {
                        return@collect
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
