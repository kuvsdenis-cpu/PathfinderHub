package com.pathfinder.hub.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportHistoryUiState(
    val isLoading: Boolean = true,
    val reports: List<ReportEntity> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ReportHistoryViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportHistoryUiState())
    val state: StateFlow<ReportHistoryUiState> = _state.asStateFlow()

    init {
        loadReports()
    }

    private fun loadReports() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            // Используем существующий метод observeAllReports из ReportRepository
            reportRepository.observeAllReports(100).collect { reports ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        reports = reports
                    )
                }
            }
        }
    }
}