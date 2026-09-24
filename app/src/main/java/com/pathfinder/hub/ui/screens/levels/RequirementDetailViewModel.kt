package com.pathfinder.hub.ui.screens.levels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RequirementDetailUiState(
    val isLoading: Boolean = true,
    val requirement: LevelRequirementEntity? = null,
    val status: String = "not_started",
    val comment: String? = null,
    val levelName: String = "",
    val sectionName: String = "",
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class RequirementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(RequirementDetailUiState())
    val state: StateFlow<RequirementDetailUiState> = _state.asStateFlow()

    init {
        loadRequirement()
    }

    private fun loadRequirement() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
            return
        }

        viewModelScope.launch {
            // Загружаем требование
            val requirements = levelRepository.getRequirementsForSection(levelId, requirementId)
            val req = requirements.firstOrNull()
            if (req == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Требование не найдено") }
                return@launch
            }

            // Названия уровня и раздела
            val level = levelRepository.getLevel(levelId)
            val sections = levelRepository.getSections(levelId)
            val section = sections.firstOrNull { it.id == req.sectionId }

            // Прогресс
            val progress = levelRepository.getProgressSnapshot(userId, levelId)
                .firstOrNull { it.requirementId == requirementId }

            _state.update {
                it.copy(
                    isLoading = false,
                    requirement = req,
                    status = progress?.status ?: "not_started",
                    comment = progress?.comment,
                    levelName = level?.name ?: "",
                    sectionName = section?.name ?: ""
                )
            }
        }
    }

    /** Отметить требование как выполненное (отправить на проверку). */
    fun markAsCompleted() {
        val userId = sessionManager.getUserId() ?: return
        val req = _state.value.requirement ?: return

        _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            try {
                levelRepository.upsertProgress(
                    LevelProgressEntity(
                        userId = userId,
                        levelId = levelId,
                        requirementId = requirementId,
                        status = "submitted",
                        submittedAt = Date(),
                        approvedBy = null,
                        approvedAt = null,
                        comment = null,
                        pendingSync = true
                    )
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        status = "submitted",
                        successMessage = "Требование отправлено на проверку"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    /** Сбросить отметку (отменить сдачу). */
    fun resetStatus() {
        val userId = sessionManager.getUserId() ?: return

        _state.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            try {
                levelRepository.clearProgressForRequirement(userId, requirementId)
                _state.update {
                    it.copy(
                        isSaving = false,
                        status = "not_started",
                        successMessage = "Отметка сброшена"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка")
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}