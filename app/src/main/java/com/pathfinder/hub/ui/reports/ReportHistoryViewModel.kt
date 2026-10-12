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
    val canArchive: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ReportHistoryViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val reportRepository: ReportRepository,
    private val userRepository: com.pathfinder.hub.data.repository.UserRepository,
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
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            val canArchive = com.pathfinder.hub.domain.permissions.PermissionChecker
                .canArchive(user)
            _state.update { it.copy(canArchive = canArchive) }

            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }

            // observeClubReports уже фильтрует archivedAt IS NULL
            reportRepository.observeClubReports(clubId).collect { reports ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        reports = reports,
                    )
                }
            }
        }
    }

    fun archiveReport(id: String) {
        viewModelScope.launch {
            try {
                reportRepository.archiveReport(id)
                _state.update { it.copy(infoMessage = "Отчёт в архиве") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update {
        it.copy(errorMessage = null, infoMessage = null)
    }
}