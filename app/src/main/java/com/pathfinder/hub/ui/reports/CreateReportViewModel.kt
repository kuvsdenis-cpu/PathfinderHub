package com.pathfinder.hub.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ReportRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateReportUiState(
    val level: String = "club",
    val periodStart: Date? = null,
    val periodEnd: Date? = null,
    val format: String = "pdf",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
)

@HiltViewModel
class CreateReportViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateReportUiState())
    val state: StateFlow<CreateReportUiState> = _state.asStateFlow()

    fun onLevelChange(v: String) = _state.update { it.copy(level = v) }
    fun onPeriodStartChange(v: Date) = _state.update { it.copy(periodStart = v) }
    fun onPeriodEndChange(v: Date) = _state.update { it.copy(periodEnd = v) }
    fun onFormatChange(v: String) = _state.update { it.copy(format = v) }

    fun generateReport() {
        val s = _state.value
        if (s.periodStart == null || s.periodEnd == null) {
            _state.update { it.copy(errorMessage = "Укажите период отчёта") }
            return
        }
        if (s.periodEnd.before(s.periodStart)) {
            _state.update { it.copy(errorMessage = "Конец периода раньше начала") }
            return
        }

        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                val report = ReportEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = "default_template",
                    level = s.level,
                    clubId = clubId,
                    periodStart = s.periodStart,
                    periodEnd = s.periodEnd,
                    format = s.format,
                    status = "draft",
                    fileUrl = null,
                    generatedBy = userId,
                    generatedAt = Date(),
                    signed = false,
                    signedAt = null,
                    errorMessage = null,
                )

                reportRepository.upsertReport(report)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}
