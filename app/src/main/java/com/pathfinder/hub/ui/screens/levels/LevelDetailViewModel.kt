package com.pathfinder.hub.ui.screens.levels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequirementUiModel(
    val id: String,
    val sectionId: String,
    val text: String,
    val type: String,
    val order: Int,
    val status: String // "not_started", "in_progress", "submitted", "approved", "rejected"
)

data class SectionUiModel(
    val id: String,
    val name: String,
    val order: Int,
    val requirements: List<RequirementUiModel>
)

data class LevelDetailUiState(
    val isLoading: Boolean = true,
    val level: LevelEntity? = null,
    val sections: List<SectionUiModel> = emptyList(),
    val approvedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class LevelDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val levelRepository: LevelRepository
) : ViewModel() {

    // 1. Получаем levelId из аргументов навигации (например, "friend")
    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""

    private val _state = MutableStateFlow(LevelDetailUiState())
    val state: StateFlow<LevelDetailUiState> = _state.asStateFlow()

    init {
        loadLevelData()
    }

    private fun loadLevelData() {
        if (levelId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "ID ступени не указан") }
            return
        }

        val userId = sessionManager.getUserId() ?: "demo_user"

        viewModelScope.launch {
            try {
                // 2. Загружаем саму ступень (один раз)
                val level = levelRepository.getLevel(levelId)

                // 3. Объединяем потоки разделов, требований и прогресса
                combine(
                    levelRepository.observeSections(levelId),
                    levelRepository.observeRequirements(levelId),
                    levelRepository.observeProgress(userId, levelId)
                ) { sections, requirements, progressList ->

                    val progressMap = progressList.associateBy { it.requirementId }
                    var approvedCount = 0

                    val uiSections = sections.map { section ->
                        val reqs = requirements.filter { it.sectionId == section.id }.map { req ->
                            val progress = progressMap[req.id]
                            val status = progress?.status ?: "not_started"
                            if (status == "approved") approvedCount++

                            RequirementUiModel(
                                id = req.id,
                                sectionId = req.sectionId,
                                text = req.text,
                                type = req.type,
                                order = req.order,
                                status = status
                            )
                        }.sortedBy { it.order }

                        SectionUiModel(
                            id = section.id,
                            name = section.name,
                            order = section.order,
                            requirements = reqs
                        )
                    }.sortedBy { it.order }

                    LevelDetailUiState(
                        isLoading = false,
                        level = level,
                        sections = uiSections,
                        approvedCount = approvedCount,
                        totalCount = requirements.size
                    )
                }.collect { newState ->
                    _state.update { newState }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    // Заглушка для действия. В будущем здесь будет вызов UseCase (например, SubmitRequirementUseCase)
    fun onRequirementAction(requirementId: String, type: String) {
        // TODO: Вызвать UseCase для изменения статуса на "submitted" или открытия экрана загрузки отчета
        println("Действие для требования: $requirementId, тип: $type")
    }
}