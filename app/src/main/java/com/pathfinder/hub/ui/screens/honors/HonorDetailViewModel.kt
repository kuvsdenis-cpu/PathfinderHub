package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HonorRequirementUiModel(
    val requirement: HonorRequirementEntity,
    val status: String,
    val comment: String?
)

data class HonorDetailUiState(
    val isLoading: Boolean = true,
    val honor: HonorEntity? = null,
    val requirements: List<HonorRequirementUiModel> = emptyList(),
    val approvedCount: Int = 0,
    val totalCount: Int = 0,
    val progressPercent: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class HonorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""

    private val _state = MutableStateFlow(HonorDetailUiState())
    val state: StateFlow<HonorDetailUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            loadHonor(userId)
        }
    }

    private fun loadHonor(userId: String) {
        viewModelScope.launch {
            val honor = honorRepository.getHonor(honorId)
            if (honor == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Специализация не найдена") }
                return@launch
            }

            _state.update { it.copy(honor = honor) }

            combine(
                honorRepository.observeRequirements(honorId),
                honorRepository.observeProgress(userId, honorId)
            ) { requirements, progress ->
                requirements to progress
            }.collect { (requirements, progress) ->
                val progressMap = progress.associateBy { it.requirementId }
                val models = requirements.map { req ->
                    val p = progressMap[req.id]
                    HonorRequirementUiModel(
                        requirement = req,
                        status = p?.status ?: "not_started",
                        comment = p?.comment
                    )
                }
                val approved = models.count { it.status == "approved" }
                val total = models.size
                val percent = if (total > 0) (approved * 100) / total else 0

                _state.update {
                    it.copy(
                        isLoading = false,
                        requirements = models,
                        approvedCount = approved,
                        totalCount = total,
                        progressPercent = percent
                    )
                }
            }
        }
    }
}