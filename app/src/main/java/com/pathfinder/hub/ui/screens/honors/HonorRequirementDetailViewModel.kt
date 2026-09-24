package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class HonorRequirementDetailUiState(
    val isLoading: Boolean = true,
    val requirement: HonorRequirementEntity? = null,
    val status: String = "not_started",
    val comment: String? = null,
    val honorName: String = "",
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HonorRequirementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(HonorRequirementDetailUiState())
    val state: StateFlow<HonorRequirementDetailUiState> = _state.asStateFlow()

    init { loadRequirement() }

    private fun loadRequirement() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
            return
        }
        viewModelScope.launch {
            val honor = honorRepository.getHonor(honorId)
            val reqs = honorRepository.getRequirements(honorId)
            val req = reqs.firstOrNull { it.id == requirementId }
            if (req == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Требование не найдено") }
                return@launch
            }
            val progressList = honorRepository.getProgressSnapshot(userId, honorId)
            val p = progressList.firstOrNull { it.requirementId == requirementId }
            _state.update {
                it.copy(
                    isLoading = false,
                    requirement = req,
                    status = p?.status ?: "not_started",
                    comment = p?.comment,
                    honorName = honor?.name ?: ""
                )
            }
        }
    }

    fun submitTheory() {
        val userId = sessionManager.getUserId() ?: return
        _state.update { it.copy(isSaving = true, successMessage = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                honorRepository.upsertProgress(
                    HonorProgressEntity(
                        userId = userId, honorId = honorId, requirementId = requirementId,
                        status = "submitted", submittedAt = Date(),
                        approvedBy = null, approvedAt = null,
                        mediaUrls = emptyList(), comment = null, pendingSync = true
                    )
                )
                _state.update {
                    it.copy(isSaving = false, status = "submitted",
                        successMessage = "Отправлено на проверку")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
