package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditUiState(
    val isLoading: Boolean = true,
    val entries: List<AuditLogEntity> = emptyList(),
    val filtered: List<AuditLogEntity> = emptyList(),
    val filterType: String? = null,
    val filterAction: String? = null,
    val filterUser: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuditUiState())
    val state: StateFlow<AuditUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeAudit(uid)
        }
    }

    private fun observeAudit(userId: String) {
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update {
                            it.copy(isLoading = false, entries = emptyList(), filtered = emptyList())
                        }
                        return@collectLatest
                    }
                    android.util.Log.d("AuditVM", "observeAudit: clubId=$clubId, starting flow")
                    auditRepository.observeClubAuditIncludingNull(clubId, 200).collect { list ->
                        android.util.Log.d("AuditVM", "observeAudit: clubId=$clubId, entries=${list.size}")
                        _state.update { s ->
                            s.copy(
                                isLoading = false,
                                entries = list,
                                filtered = applyFilters(list, s.filterType, s.filterAction, s.filterUser),
                            )
                        }
                    }
                }
        }
    }

    fun setFilterType(type: String?) = _state.update { s ->
        s.copy(
            filterType = type,
            filtered = applyFilters(s.entries, type, s.filterAction, s.filterUser),
        )
    }

    fun setFilterAction(action: String?) = _state.update { s ->
        s.copy(
            filterAction = action,
            filtered = applyFilters(s.entries, s.filterType, action, s.filterUser),
        )
    }

    fun setFilterUser(text: String) = _state.update { s ->
        s.copy(
            filterUser = text,
            filtered = applyFilters(s.entries, s.filterType, s.filterAction, text),
        )
    }

    fun resetFilters() = _state.update { s ->
        s.copy(
            filterType = null,
            filterAction = null,
            filterUser = "",
            filtered = applyFilters(s.entries, null, null, ""),
        )
    }

    private fun applyFilters(
        list: List<AuditLogEntity>,
        type: String?,
        action: String?,
        user: String,
    ): List<AuditLogEntity> = list.filter { e ->
        (type == null || e.targetType == type) &&
            (action == null || e.action == action) &&
            (user.isBlank() || e.userId.contains(user, ignoreCase = true))
    }
}
