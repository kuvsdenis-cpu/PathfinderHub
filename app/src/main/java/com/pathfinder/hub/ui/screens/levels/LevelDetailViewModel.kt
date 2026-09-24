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
    val requirement: LevelRequirementEntity,
    val status: String,  // not_started | in_progress | submitted | approved | rejected
    val comment: String?
)

data class SectionUiModel(
    val section: LevelSectionEntity,
    val requirements: List<RequirementUiModel>,
    val isExpanded: Boolean,
    val approvedCount: Int,
    val totalCount: Int
)

data class LevelDetailUiState(
    val isLoading: Boolean = true,
    val level: LevelEntity? = null,
    val sections: List<SectionUiModel> = emptyList(),
    val approvedCount: Int = 0,
    val totalCount: Int = 0,
    val progressPercent: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class LevelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val levelRepository: LevelRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""

    private val _state = MutableStateFlow(LevelDetailUiState())
    val state: StateFlow<LevelDetailUiState> = _state.asStateFlow()

    // Храним состояние раскрытых разделов отдельно (переживает перезагрузку данных)
    private val expandedSections = mutableSetOf<String>()

    init {
        loadLevel()
    }

    private fun loadLevel() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет активной сессии") }
            return
        }

        viewModelScope.launch {
            // Загружаем уровень один раз
            val level = levelRepository.getLevel(levelId)
            if (level == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Ступень не найдена") }
                return@launch
            }

            _state.update { it.copy(level = level) }

            // Реактивно следим за разделами, требованиями и прогрессом
            combine(
                levelRepository.observeSections(levelId),
                levelRepository.observeRequirements(levelId),
                levelRepository.observeProgress(userId, levelId)
            ) { sections, requirements, progress ->
                Triple(sections, requirements, progress)
            }.collect { (sections, requirements, progress) ->
                val progressMap = progress.associateBy { it.requirementId }

                val sectionModels = sections.map { section ->
                    val sectionReqs = requirements.filter { it.sectionId == section.id }
                    val reqModels = sectionReqs.map { req ->
                        val p = progressMap[req.id]
                        RequirementUiModel(
                            requirement = req,
                            status = p?.status ?: "not_started",
                            comment = p?.comment
                        )
                    }
                    val approved = reqModels.count { it.status == "approved" }

                    SectionUiModel(
                        section = section,
                        requirements = reqModels,
                        isExpanded = expandedSections.contains(section.id),
                        approvedCount = approved,
                        totalCount = reqModels.size
                    )
                }

                val totalApproved = sectionModels.sumOf { it.approvedCount }
                val totalReqs = sectionModels.sumOf { it.totalCount }
                val percent = if (totalReqs > 0) (totalApproved * 100) / totalReqs else 0

                _state.update {
                    it.copy(
                        isLoading = false,
                        sections = sectionModels,
                        approvedCount = totalApproved,
                        totalCount = totalReqs,
                        progressPercent = percent
                    )
                }
            }
        }
    }

    fun toggleSection(sectionId: String) {
        if (expandedSections.contains(sectionId)) {
            expandedSections.remove(sectionId)
        } else {
            expandedSections.add(sectionId)
        }
        // Обновляем UI
        _state.update { state ->
            state.copy(
                sections = state.sections.map { s ->
                    if (s.section.id == sectionId) s.copy(isExpanded = !s.isExpanded) else s
                }
            )
        }
    }
}