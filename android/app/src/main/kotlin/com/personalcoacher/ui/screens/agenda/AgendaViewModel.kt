package com.personalcoacher.ui.screens.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.repository.AgendaRepository
import com.personalcoacher.domain.repository.EventNotificationRepository
import com.personalcoacher.util.Result
import com.personalcoacher.util.onError
import com.personalcoacher.util.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AgendaUiState(
    val items: List<AgendaItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showEditor: Boolean = false,
    val editorState: AgendaEditorState = AgendaEditorState()
)

data class AgendaEditorState(
    val title: String = "",
    val description: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val isAllDay: Boolean = false,
    val location: String = "",
    val isSaving: Boolean = false,
    val editingItemId: String? = null,
    // Event notification settings
    val notifyBefore: Boolean = false,
    val minutesBefore: Int = 30,
    val notifyAfter: Boolean = false,
    val minutesAfter: Int = 60,
    val isAnalyzingNotifications: Boolean = false,
    val notificationSettingsExpanded: Boolean = false,
    val hasNotificationSettings: Boolean = false,
    val isLoadingNotificationSettings: Boolean = false
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val agendaRepository: AgendaRepository,
    private val eventNotificationRepository: EventNotificationRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            val userId = tokenManager.awaitUserId()

            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "User not logged in") }
                return@launch
            }

            currentUserId = userId

            // Collect agenda items
            agendaRepository.getAgendaItems(userId).collect { items ->
                _uiState.update {
                    it.copy(
                        items = items.sortedBy { item -> item.startTime },
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun openNewItem() {
        _uiState.update {
            it.copy(
                showEditor = true,
                editorState = AgendaEditorState(
                    startDate = it.selectedDate,
                    endDate = it.selectedDate
                )
            )
        }
    }

    fun openEditItem(item: AgendaItem) {
        val startDateTime = item.startTime.atZone(ZoneId.systemDefault())
        val endDateTime = item.endTime?.atZone(ZoneId.systemDefault())

        _uiState.update {
            it.copy(
                showEditor = true,
                editorState = AgendaEditorState(
                    title = item.title,
                    description = item.description ?: "",
                    startDate = startDateTime.toLocalDate(),
                    startTime = startDateTime.toLocalTime(),
                    endDate = endDateTime?.toLocalDate() ?: startDateTime.toLocalDate(),
                    endTime = endDateTime?.toLocalTime() ?: startDateTime.toLocalTime().plusHours(1),
                    isAllDay = item.isAllDay,
                    location = item.location ?: "",
                    editingItemId = item.id,
                    isLoadingNotificationSettings = true
                )
            )
        }

        // Load existing notification settings
        loadNotificationSettings(item.id)
    }

    private fun loadNotificationSettings(agendaItemId: String) {
        viewModelScope.launch {
            eventNotificationRepository.getNotificationForAgendaItem(agendaItemId).collect { notification ->
                _uiState.update {
                    if (notification != null) {
                        it.copy(
                            editorState = it.editorState.copy(
                                notifyBefore = notification.notifyBefore,
                                minutesBefore = notification.minutesBefore ?: 30,
                                notifyAfter = notification.notifyAfter,
                                minutesAfter = notification.minutesAfter ?: 60,
                                hasNotificationSettings = true,
                                isLoadingNotificationSettings = false
                            )
                        )
                    } else {
                        it.copy(
                            editorState = it.editorState.copy(
                                isLoadingNotificationSettings = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                showEditor = false,
                editorState = AgendaEditorState()
            )
        }
    }

    fun updateTitle(title: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(title = title))
        }
    }

    fun updateDescription(description: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(description = description))
        }
    }

    fun updateStartDate(date: LocalDate) {
        _uiState.update {
            val newEditorState = it.editorState.copy(startDate = date)
            // If end date is before start date, update end date too
            if (newEditorState.endDate.isBefore(date)) {
                it.copy(editorState = newEditorState.copy(endDate = date))
            } else {
                it.copy(editorState = newEditorState)
            }
        }
    }

    fun updateStartTime(time: LocalTime) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(startTime = time))
        }
    }

    fun updateEndDate(date: LocalDate) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(endDate = date))
        }
    }

    fun updateEndTime(time: LocalTime) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(endTime = time))
        }
    }

    fun updateIsAllDay(isAllDay: Boolean) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(isAllDay = isAllDay))
        }
    }

    fun updateLocation(location: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(location = location))
        }
    }

    fun updateNotifyBefore(notifyBefore: Boolean) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(notifyBefore = notifyBefore))
        }
    }

    fun updateMinutesBefore(minutes: Int) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(minutesBefore = minutes.coerceIn(5, 1440)))
        }
    }

    fun updateNotifyAfter(notifyAfter: Boolean) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(notifyAfter = notifyAfter))
        }
    }

    fun updateMinutesAfter(minutes: Int) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(minutesAfter = minutes.coerceIn(5, 1440)))
        }
    }

    fun toggleNotificationSettingsExpanded() {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(
                notificationSettingsExpanded = !it.editorState.notificationSettingsExpanded
            ))
        }
    }

    fun saveItem() {
        val userId = currentUserId ?: return
        val editorState = _uiState.value.editorState

        if (editorState.title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(editorState = it.editorState.copy(isSaving = true)) }

            val startTime = if (editorState.isAllDay) {
                LocalDateTime.of(editorState.startDate, LocalTime.MIDNIGHT)
                    .atZone(ZoneId.systemDefault()).toInstant()
            } else {
                LocalDateTime.of(editorState.startDate, editorState.startTime)
                    .atZone(ZoneId.systemDefault()).toInstant()
            }

            val endTime = if (editorState.isAllDay) {
                LocalDateTime.of(editorState.endDate, LocalTime.of(23, 59))
                    .atZone(ZoneId.systemDefault()).toInstant()
            } else {
                LocalDateTime.of(editorState.endDate, editorState.endTime)
                    .atZone(ZoneId.systemDefault()).toInstant()
            }

            // Validate that end time is not before start time
            if (endTime.isBefore(startTime)) {
                _uiState.update {
                    it.copy(
                        error = "End time cannot be before start time",
                        editorState = it.editorState.copy(isSaving = false)
                    )
                }
                return@launch
            }

            val result = if (editorState.editingItemId != null) {
                agendaRepository.updateAgendaItem(
                    id = editorState.editingItemId,
                    title = editorState.title,
                    description = editorState.description.ifBlank { null },
                    startTime = startTime,
                    endTime = endTime,
                    isAllDay = editorState.isAllDay,
                    location = editorState.location.ifBlank { null }
                )
            } else {
                agendaRepository.createAgendaItem(
                    userId = userId,
                    title = editorState.title,
                    description = editorState.description.ifBlank { null },
                    startTime = startTime,
                    endTime = endTime,
                    isAllDay = editorState.isAllDay,
                    location = editorState.location.ifBlank { null }
                )
            }

            result
                .onSuccess { createdItem ->
                    if (editorState.editingItemId == null) {
                        // New item: Trigger AI analysis in background
                        analyzeEventNotifications(
                            agendaItemId = createdItem.id,
                            userId = userId,
                            title = createdItem.title,
                            description = createdItem.description,
                            startTime = createdItem.startTime.toEpochMilli(),
                            endTime = createdItem.endTime?.toEpochMilli(),
                            isAllDay = createdItem.isAllDay,
                            location = createdItem.location
                        )
                    } else {
                        // Editing existing item: Save user's notification settings
                        saveUserNotificationSettings(
                            agendaItemId = createdItem.id,
                            userId = userId
                        )
                    }
                    closeEditor()
                }
                .onError { error ->
                    _uiState.update {
                        it.copy(
                            error = error,
                            editorState = it.editorState.copy(isSaving = false)
                        )
                    }
                }
        }
    }

    /**
     * Saves user-configured notification settings for an event.
     * This is called when editing an existing event.
     */
    private fun saveUserNotificationSettings(agendaItemId: String, userId: String) {
        val editorState = _uiState.value.editorState
        viewModelScope.launch {
            eventNotificationRepository.updateNotificationSettings(
                agendaItemId = agendaItemId,
                notifyBefore = editorState.notifyBefore,
                minutesBefore = editorState.minutesBefore,
                notifyAfter = editorState.notifyAfter,
                minutesAfter = editorState.minutesAfter
            )
        }
    }

    /**
     * Analyzes an event with AI to determine if notifications should be sent.
     * This runs in the background and doesn't block the UI.
     */
    private fun analyzeEventNotifications(
        agendaItemId: String,
        userId: String,
        title: String,
        description: String?,
        startTime: Long,
        endTime: Long?,
        isAllDay: Boolean,
        location: String?
    ) {
        viewModelScope.launch {
            val analysisResult = eventNotificationRepository.analyzeAgendaItem(
                agendaItemId = agendaItemId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                location = location
            )

            when (analysisResult) {
                is Result.Success -> {
                    val analysis = analysisResult.data
                    // Save the notification settings based on AI analysis
                    eventNotificationRepository.saveNotificationSettings(
                        agendaItemId = agendaItemId,
                        userId = userId,
                        notifyBefore = analysis.shouldNotifyBefore,
                        minutesBefore = analysis.minutesBefore,
                        beforeMessage = analysis.beforeMessage,
                        notifyAfter = analysis.shouldNotifyAfter,
                        minutesAfter = analysis.minutesAfter,
                        afterMessage = analysis.afterMessage,
                        aiDetermined = true,
                        aiReasoning = analysis.reasoning
                    )
                }
                is Result.Error -> {
                    // Silently fail - notification analysis is optional
                    // Could log or show a non-intrusive message if needed
                }
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            agendaRepository.deleteAgendaItem(itemId)
                .onError { error ->
                    _uiState.update { it.copy(error = error) }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadItems()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Get items for a specific date
    fun getItemsForDate(date: LocalDate): List<AgendaItem> {
        return _uiState.value.items.filter { item ->
            val itemDate = item.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            itemDate == date
        }
    }

    // Get upcoming items (for home screen)
    fun getUpcomingItems(limit: Int = 3): List<AgendaItem> {
        val now = Instant.now()
        return _uiState.value.items
            .filter { it.startTime.isAfter(now) }
            .take(limit)
    }
}
