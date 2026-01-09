package com.personalcoacher.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.util.Resource
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
    val editorState: JournalEditorState = JournalEditorState()
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
    private val tokenManager: TokenManager
) : ViewModel() {

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
}
