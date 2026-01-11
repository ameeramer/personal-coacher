package com.personalcoacher.ui.screens.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.util.DateUtils
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
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
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

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
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
