package com.pathfinder.hub.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ReportRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportHistoryViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportHistoryUiState())
    val state: StateFlow<ReportHistoryUiState> = _state.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        // canArchive
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                _state.update { it.copy(canArchive = PermissionChecker.canArchive(user)) }
            }
        }

        // reports: clubId → перезагрузка
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .flatMapLatest { clubId ->
                    if (clubId.isNullOrBlank()) flowOf(emptyList())
                    else reportRepository.observeClubReports(clubId)
                }
                .collect { reports ->
                    _state.update { it.copy(isLoading = false, reports = reports) }
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
