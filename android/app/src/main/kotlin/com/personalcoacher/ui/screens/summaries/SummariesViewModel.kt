package com.personalcoacher.ui.screens.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.domain.model.Summary
import com.personalcoacher.domain.model.SummaryType
import com.personalcoacher.domain.repository.SummaryRepository
import com.personalcoacher.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummariesUiState(
    val summaries: List<Summary> = emptyList(),
    val selectedType: SummaryType? = null,
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedSummary: Summary? = null
)

@HiltViewModel
class SummariesViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummariesUiState())
    val uiState: StateFlow<SummariesUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadSummaries()
    }

    private fun loadSummaries() {
        viewModelScope.launch {
            currentUserId = tokenManager.currentUserId.first()
            val userId = currentUserId ?: return@launch

            _uiState.update { it.copy(isLoading = true) }

            summaryRepository.getSummaries(userId).collect { summaries ->
                _uiState.update {
                    it.copy(
                        summaries = summaries,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun filterByType(type: SummaryType?) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            _uiState.update { it.copy(selectedType = type, isLoading = true) }

            val summariesFlow = if (type != null) {
                summaryRepository.getSummariesByType(userId, type)
            } else {
                summaryRepository.getSummaries(userId)
            }

            summariesFlow.collect { summaries ->
                _uiState.update {
                    it.copy(
                        summaries = summaries,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            _uiState.update { it.copy(isRefreshing = true) }

            when (val result = summaryRepository.syncSummaries(userId)) {
                is Resource.Error -> {
                    _uiState.update { it.copy(isRefreshing = false, error = result.message) }
                }
                else -> {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun generateSummary(type: SummaryType) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch

            _uiState.update { it.copy(isGenerating = true, error = null) }

            when (val result = summaryRepository.generateSummary(userId, type)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isGenerating = false) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isGenerating = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun selectSummary(summary: Summary) {
        _uiState.update { it.copy(selectedSummary = summary) }
    }

    fun clearSelectedSummary() {
        _uiState.update { it.copy(selectedSummary = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
