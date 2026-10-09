package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParentPaymentsUiState(
    val isLoading: Boolean = true,
    val payments: List<PaymentEntity> = emptyList(),
    val processingId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ParentPaymentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentPaymentsUiState())
    val state: StateFlow<ParentPaymentsUiState> = _state.asStateFlow()

    private val parentId: String? = sessionManager.getUserId()

    init {
        val uid = parentId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                parentRepository.observeParentPayments(uid).collect { list ->
                    _state.update { it.copy(isLoading = false, payments = list) }
                }
            }
        }
    }

    /**
     * Родитель отмечает оплату. Статус меняется на "paid".
     * Директор потом подтверждает → "confirmed".
     */
    fun markPaid(payment: PaymentEntity) {
        val uid = parentId ?: return
        _state.update { it.copy(processingId = payment.id, errorMessage = null) }
        viewModelScope.launch {
            try {
                parentRepository.upsertPayment(
                    payment.copy(
                        status = "paid",
                        paidAt = java.util.Date(),
                        paymentMethod = "manual",
                    )
                )
                _state.update {
                    it.copy(processingId = null, infoMessage = "Отмечено как оплачено")
                }
            } catch (e: Exception) {
                _state.update { it.copy(processingId = null, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
