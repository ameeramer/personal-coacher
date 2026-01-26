package com.personalcoacher.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Goal
import com.personalcoacher.domain.model.Priority
import com.personalcoacher.domain.model.Task
import com.personalcoacher.domain.repository.GoalRepository
import com.personalcoacher.domain.repository.TaskRepository
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

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val availableGoals: List<Goal> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class TaskEditorState(
    val title: String = "",
    val description: String = "",
    val dueDate: LocalDate? = null,
    val priority: Priority = Priority.MEDIUM,
    val linkedGoalId: String? = null,
    val isSaving: Boolean = false,
    val editingTaskId: String? = null,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val goalRepository: GoalRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _tasksState = MutableStateFlow(TasksUiState())
    val tasksState: StateFlow<TasksUiState> = _tasksState.asStateFlow()

    private val _editorState = MutableStateFlow(TaskEditorState())
    val editorState: StateFlow<TaskEditorState> = _editorState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadTasks()
    }

    private fun loadTasks() {
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
                _tasksState.update { it.copy(isLoading = false, error = "Unable to load user") }
                return@launch
            }

            _tasksState.update { it.copy(isLoading = true) }

            // Load tasks
            launch {
                taskRepository.getTasks(userId).collect { tasks ->
                    _tasksState.update {
                        it.copy(
                            tasks = tasks,
                            isLoading = false
                        )
                    }
                }
            }

            // Load available goals for linking
            launch {
                goalRepository.getActiveGoals(userId).collect { goals ->
                    _tasksState.update { it.copy(availableGoals = goals) }
                }
            }
        }
    }

    fun loadTaskForEditing(taskId: String) {
        viewModelScope.launch {
            taskRepository.getTaskById(taskId).first()?.let { task ->
                _editorState.update {
                    it.copy(
                        title = task.title,
                        description = task.description,
                        dueDate = task.dueDate,
                        priority = task.priority,
                        linkedGoalId = task.linkedGoalId,
                        editingTaskId = taskId
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

    fun updateDueDate(date: LocalDate?) {
        _editorState.update { it.copy(dueDate = date) }
    }

    fun updatePriority(priority: Priority) {
        _editorState.update { it.copy(priority = priority) }
    }

    fun updateLinkedGoal(goalId: String?) {
        _editorState.update { it.copy(linkedGoalId = goalId) }
    }

    fun clearEditorState() {
        _editorState.value = TaskEditorState()
    }

    fun saveTask() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val state = _editorState.value

            if (state.title.isBlank()) {
                _editorState.update { it.copy(error = "Please add a title") }
                return@launch
            }

            _editorState.update { it.copy(isSaving = true) }

            val result = if (state.editingTaskId != null) {
                taskRepository.updateTask(
                    id = state.editingTaskId,
                    title = state.title,
                    description = state.description,
                    dueDate = state.dueDate,
                    priority = state.priority,
                    linkedGoalId = state.linkedGoalId
                )
            } else {
                taskRepository.createTask(
                    userId = userId,
                    title = state.title,
                    description = state.description,
                    dueDate = state.dueDate,
                    priority = state.priority,
                    linkedGoalId = state.linkedGoalId
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

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleTaskCompletion(task.id)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task.id)
        }
    }

    fun clearError() {
        _tasksState.update { it.copy(error = null) }
        _editorState.update { it.copy(error = null) }
    }
}
