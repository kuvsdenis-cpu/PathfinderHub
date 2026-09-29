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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CreateReportUiState(
    val level: String = "club",
    val periodStart: String = "",
    val periodEnd: String = "",
    val format: String = "pdf",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false
)

@HiltViewModel
class CreateReportViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateReportUiState())
    val state: StateFlow<CreateReportUiState> = _state.asStateFlow()

    fun onLevelChange(value: String) = _state.update { it.copy(level = value) }
    fun onPeriodStartChange(value: String) = _state.update { it.copy(periodStart = value) }
    fun onPeriodEndChange(value: String) = _state.update { it.copy(periodEnd = value) }
    fun onFormatChange(value: String) = _state.update { it.copy(format = value) }

    fun generateReport() {
        val s = _state.value
        if (s.periodStart.isBlank() || s.periodEnd.isBlank()) {
            _state.update { it.copy(errorMessage = "Укажите период отчёта") }
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

                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateStart = fmt.parse(s.periodStart) ?: Date()
                val dateEnd = fmt.parse(s.periodEnd) ?: Date()

                val report = ReportEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = "default_template", // Можно доработать выбор из observeTemplates
                    level = s.level,
                    clubId = clubId,
                    periodStart = dateStart,
                    periodEnd = dateEnd,
                    format = s.format,
                    status = "draft",
                    fileUrl = null,
                    generatedBy = userId,
                    generatedAt = Date(),
                    signed = false,
                    signedAt = null,
                    errorMessage = null
                )

                reportRepository.upsertReport(report)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания") }
            }
        }
    }
}