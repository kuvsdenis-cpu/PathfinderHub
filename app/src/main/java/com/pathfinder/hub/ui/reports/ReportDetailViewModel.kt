package com.pathfinder.hub.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportDetailUiState(
    val isLoading: Boolean = true,
    val report: ReportEntity? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportDetailUiState())
    val state: StateFlow<ReportDetailUiState> = _state.asStateFlow()

    fun loadReport(reportId: String) {
        viewModelScope.launch {
            try {
                val report = reportRepository.getReportById(reportId) // Убедитесь, что этот метод есть в ReportRepository
                _state.update {
                    it.copy(
                        isLoading = false,
                        report = report,
                        errorMessage = if (report == null) "Отчёт не найден" else null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}