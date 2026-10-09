package com.pathfinder.hub.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectorPaymentsUiState(
    val isLoading: Boolean = true,
    val all: List<PaymentEntity> = emptyList(),
    val filtered: List<PaymentEntity> = emptyList(),
    val selectedFilter: String = "waiting",
    val namesByParent: Map<String, String> = emptyMap(),
    val processingId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class DirectorPaymentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorPaymentsUiState())
    val state: StateFlow<DirectorPaymentsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            userRepository.observeClubMembersByRole(clubId, "parent").collect { parents ->
                val names = parents.associate {
                    it.id to "${it.firstName} ${it.lastName}".trim()
                }
                _state.update { it.copy(namesByParent = names, isLoading = false) }

                parents.forEach { p ->
                    launch {
                        parentRepository.observeParentPayments(p.id).collect { list ->
                            _state.update { s ->
                                val others = s.all.filter { it.parentId != p.id }
                                val merged = (others + list).sortedByDescending { it.dueDate }
                                s.copy(all = merged, filtered = applyFilter(merged, s.selectedFilter))
                            }
                        }
                    }
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _state.update {
            it.copy(selectedFilter = filter, filtered = applyFilter(it.all, filter))
        }
    }

    private fun applyFilter(list: List<PaymentEntity>, filter: String): List<PaymentEntity> {
        return when (filter) {
            "waiting" -> list.filter {
                it.status == "pending" || it.status == "paid" || it.status == "overdue"
            }
            "paid" -> list.filter { it.status == "paid" }
            "confirmed" -> list.filter { it.status == "confirmed" }
            else -> list
        }
    }

    fun confirm(p: PaymentEntity) {
        val uid = sessionManager.getUserId() ?: return
        _state.update { it.copy(processingId = p.id) }
        viewModelScope.launch {
            try {
                parentRepository.confirmPayment(p.id, "confirmed", uid, System.currentTimeMillis())
                _state.update {
                    it.copy(processingId = null, infoMessage = "Оплата подтверждена")
                }
            } catch (e: Exception) {
                _state.update { it.copy(processingId = null, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
