package com.personalcoacher.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.AgendaItem
import com.personalcoacher.domain.model.EventSuggestion
import com.personalcoacher.domain.model.JournalEntry
import com.personalcoacher.domain.model.Mood
import com.personalcoacher.domain.repository.AgendaRepository
import com.personalcoacher.domain.repository.JournalRepository
import com.personalcoacher.util.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class TimeOfDay {
    MORNING,    // 06:00 - 12:00
    AFTERNOON,  // 12:00 - 18:00
    EVENING,    // 18:00 - 22:00
    NIGHT       // 22:00 - 06:00
}

data class HomeUiState(
    val userName: String = "",
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    val totalEntries: Int = 0,
    val currentStreak: Int = 0,
    val hasEntryToday: Boolean = false,
    val recentMood: Mood? = null,
    val recentEntryPreview: String? = null,
    val isLoading: Boolean = true,
    val pendingEventSuggestions: List<EventSuggestion> = emptyList(),
    val upcomingAgendaItems: List<AgendaItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    private val agendaRepository: AgendaRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadData()
        updateTimeOfDay()
    }

    private fun updateTimeOfDay() {
        val hour = LocalTime.now().hour
        val timeOfDay = when {
            hour in 6..11 -> TimeOfDay.MORNING
            hour in 12..17 -> TimeOfDay.AFTERNOON
            hour in 18..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
        _uiState.update { it.copy(timeOfDay = timeOfDay) }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Wait for userId to be available
            var userId: String? = null
            var attempts = 0
            while (userId == null && attempts < 10) {
                userId = tokenManager.currentUserId.first()
                if (userId == null) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
            }

            if (userId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            currentUserId = userId

            // Get user email for display name
            val userEmail = tokenManager.getUserEmail()
            val displayName = userEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Friend"

            _uiState.update { it.copy(userName = displayName) }

            // Collect journal entries to calculate stats
            journalRepository.getEntries(userId).collect { entries ->
                val stats = calculateStats(entries)
                _uiState.update {
                    it.copy(
                        totalEntries = stats.totalEntries,
                        currentStreak = stats.currentStreak,
                        hasEntryToday = stats.hasEntryToday,
                        recentMood = stats.recentMood,
                        recentEntryPreview = stats.recentEntryPreview,
                        isLoading = false
                    )
                }
            }
        }

        // Load pending event suggestions
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

            if (userId != null) {
                agendaRepository.getPendingEventSuggestions(userId).collect { suggestions ->
                    _uiState.update { it.copy(pendingEventSuggestions = suggestions) }
                }
            }
        }

        // Load upcoming agenda items
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

            if (userId != null) {
                agendaRepository.getUpcomingAgendaItems(userId, 3).collect { items ->
                    _uiState.update { it.copy(upcomingAgendaItems = items) }
                }
            }
        }
    }

    private fun calculateStats(entries: List<JournalEntry>): JournalStats {
        if (entries.isEmpty()) {
            return JournalStats()
        }

        val today = LocalDate.now()
        val sortedEntries = entries.sortedByDescending { it.date }

        // Check if there's an entry today
        val hasEntryToday = sortedEntries.any { entry ->
            entry.date.atZone(ZoneId.systemDefault()).toLocalDate() == today
        }

        // Calculate streak
        var streak = 0
        var currentDate = if (hasEntryToday) today else today.minusDays(1)

        val entriesByDate = entries.groupBy { entry ->
            entry.date.atZone(ZoneId.systemDefault()).toLocalDate()
        }

        while (entriesByDate.containsKey(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }

        // Get recent entry info
        val recentEntry = sortedEntries.firstOrNull()
        val recentMood = recentEntry?.mood
        val recentEntryPreview = recentEntry?.content
            ?.replace(Regex("<[^>]*>"), "") // Remove HTML tags
            ?.take(100)
            ?.let { if (recentEntry.content.length > 100) "$it..." else it }

        return JournalStats(
            totalEntries = entries.size,
            currentStreak = streak,
            hasEntryToday = hasEntryToday,
            recentMood = recentMood,
            recentEntryPreview = recentEntryPreview
        )
    }

    fun refreshTimeOfDay() {
        updateTimeOfDay()
    }

    fun acceptEventSuggestion(suggestionId: String) {
        viewModelScope.launch {
            agendaRepository.acceptEventSuggestion(suggestionId)
        }
    }

    fun rejectEventSuggestion(suggestionId: String) {
        viewModelScope.launch {
            agendaRepository.rejectEventSuggestion(suggestionId)
        }
    }

    private data class JournalStats(
        val totalEntries: Int = 0,
        val currentStreak: Int = 0,
        val hasEntryToday: Boolean = false,
        val recentMood: Mood? = null,
        val recentEntryPreview: String? = null
    )
}
