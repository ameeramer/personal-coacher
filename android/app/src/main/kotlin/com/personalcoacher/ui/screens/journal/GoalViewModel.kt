package com.personalcoacher.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.GoalStatus
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.repository.GoalRepository
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

data class GoalsUiState(
    val goals: List<Goal> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class GoalEditorState(
    val title: String = "",
    val description: String = "",
    val targetDate: LocalDate? = null,
    val priority: Priority = Priority.MEDIUM,
    val isSaving: Boolean = false,
    val editingGoalId: String? = null,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _goalsState = MutableStateFlow(GoalsUiState())
    val goalsState: StateFlow<GoalsUiState> = _goalsState.asStateFlow()

    private val _editorState = MutableStateFlow(GoalEditorState())
    val editorState: StateFlow<GoalEditorState> = _editorState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadGoals()
    }

    private fun loadGoals() {
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
                _goalsState.update { it.copy(isLoading = false, error = "Unable to load user") }
                return@launch
            }

            _goalsState.update { it.copy(isLoading = true) }

            goalRepository.getGoals(userId).collect { goals ->
                _goalsState.update {
                    it.copy(
                        goals = goals,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadGoalForEditing(goalId: String) {
        viewModelScope.launch {
            goalRepository.getGoalById(goalId).first()?.let { goal ->
                _editorState.update {
                    it.copy(
                        title = goal.title,
                        description = goal.description,
                        targetDate = goal.targetDate,
                        priority = goal.priority,
                        editingGoalId = goalId
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _editorState.update { it.copy(title = title) }
    }

    fun updateDescription(description: String) {
        _editorState.update { it.copy(description = description) }
    }

    fun updateTargetDate(date: LocalDate?) {
        _editorState.update { it.copy(targetDate = date) }
    }

    fun updatePriority(priority: Priority) {
        _editorState.update { it.copy(priority = priority) }
    }

    fun clearEditorState() {
        _editorState.value = GoalEditorState()
    }

    fun saveGoal() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val state = _editorState.value

            if (state.title.isBlank()) {
                _editorState.update { it.copy(error = "Please add a title") }
                return@launch
            }

            _editorState.update { it.copy(isSaving = true) }

            val result = if (state.editingGoalId != null) {
                goalRepository.updateGoal(
                    id = state.editingGoalId,
                    title = state.title,
                    description = state.description,
                    targetDate = state.targetDate,
                    priority = state.priority
                )
            } else {
                goalRepository.createGoal(
                    userId = userId,
                    title = state.title,
                    description = state.description,
                    targetDate = state.targetDate,
                    priority = state.priority
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

    fun updateGoalStatus(goal: Goal, status: GoalStatus) {
        viewModelScope.launch {
            goalRepository.updateGoalStatus(goal.id, status)
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            goalRepository.deleteGoal(goal.id)
        }
    }

    fun clearError() {
        _goalsState.update { it.copy(error = null) }
        _editorState.update { it.copy(error = null) }
    }
}
