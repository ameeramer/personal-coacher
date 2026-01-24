package com.personalcoacher.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Note
import com.personalcoacher.domain.repository.NoteRepository
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class NoteEditorState(
    val title: String = "",
    val content: String = "",
    val isSaving: Boolean = false,
    val editingNoteId: String? = null,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _notesState = MutableStateFlow(NotesUiState())
    val notesState: StateFlow<NotesUiState> = _notesState.asStateFlow()

    private val _editorState = MutableStateFlow(NoteEditorState())
    val editorState: StateFlow<NoteEditorState> = _editorState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadNotes()
    }

    private fun loadNotes() {
        viewModelScope.launch {
            var userId: String? = null
            var attempts = 0
            while (userId == null && attempts < 10) {
                userId = tokenManager.currentUserId.first()
                if (userId == null) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
            }

            currentUserId = userId
            if (userId == null) {
                _notesState.update { it.copy(isLoading = false, error = "Unable to load user") }
                return@launch
            }

            _notesState.update { it.copy(isLoading = true) }

            noteRepository.getNotes(userId).collect { notes ->
                _notesState.update {
                    it.copy(
                        notes = notes,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadNoteForEditing(noteId: String) {
        viewModelScope.launch {
            noteRepository.getNoteById(noteId).first()?.let { note ->
                _editorState.update {
                    it.copy(
                        title = note.title,
                        content = note.content,
                        editingNoteId = noteId
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _editorState.update { it.copy(title = title) }
    }

    fun updateContent(content: String) {
        _editorState.update { it.copy(content = content) }
    }

    fun clearEditorState() {
        _editorState.value = NoteEditorState()
    }

    fun saveNote() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val state = _editorState.value

            if (state.title.isBlank() && state.content.isBlank()) {
                _editorState.update { it.copy(error = "Please add a title or content") }
                return@launch
            }

            _editorState.update { it.copy(isSaving = true) }

            val result = if (state.editingNoteId != null) {
                noteRepository.updateNote(
                    id = state.editingNoteId,
                    title = state.title,
                    content = state.content
                )
            } else {
                noteRepository.createNote(
                    userId = userId,
                    title = state.title,
                    content = state.content
                )
            }

            when (result) {
                is Resource.Success -> {
                    _editorState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                is Resource.Error -> {
                    _editorState.update { it.copy(isSaving = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.deleteNote(note.id)
        }
    }

    fun clearError() {
        _notesState.update { it.copy(error = null) }
        _editorState.update { it.copy(error = null) }
    }
}
