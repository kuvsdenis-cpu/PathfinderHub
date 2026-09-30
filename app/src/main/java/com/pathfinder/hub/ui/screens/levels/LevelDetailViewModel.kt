package com.pathfinder.hub.ui.screens.levels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LevelDetailUiState(
    val isLoading: Boolean = true,
    val level: LevelEntity? = null,
    val sections: List<SectionUiModel> = emptyList(),
    val approvedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

data class SectionUiModel(
    val id: String,
    val name: String,
    val order: Int,
    val requirements: List<RequirementUiModel>
)

data class RequirementUiModel(
    val id: String,
    val sectionId: String,
    val text: String,
    val type: String,
    val order: Int,
    val status: String
)

@HiltViewModel
class LevelDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val levelRepository: LevelRepository
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""

    private val _state = MutableStateFlow(LevelDetailUiState())
    val state: StateFlow<LevelDetailUiState> = _state.asStateFlow()

    init {
        loadLevelData()
    }

    private fun loadLevelData() {
        Log.d("LevelDetailVM", "Получен levelId: '$levelId'")
        Log.d("LevelDetailVM", "Длина levelId: ${levelId.length}, символы: ${levelId.map { "'$it'" }}")

        if (levelId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "ID ступени не указан") }
            return
        }

        val userId = sessionManager.getUserId() ?: "demo_user"

        viewModelScope.launch {
            try {
                val level = levelRepository.getLevel(levelId)
                Log.d("LevelDetailVM", "Ступень загружена: ${level?.name ?: "null"}")

                // ✅ ПРЯМОЙ синхронный запрос к БД для проверки (поможет найти причину пустого экрана)
                val directSections = levelRepository.getSections(levelId)
                val directRequirements = levelRepository.getRequirements(levelId)
                Log.d("LevelDetailVM", "🔍 ПРЯМОЙ ЗАПРОС К БД: разделов=${directSections.size}, требований=${directRequirements.size}")

                if (directRequirements.isEmpty()) {
                    Log.e("LevelDetailVM", "⚠️ КРИТИЧЕСКАЯ ОШИБКА: База данных пуста для levelId='$levelId'!")
                }

                combine(
                    levelRepository.observeSections(levelId),
                    levelRepository.observeRequirements(levelId),
                    levelRepository.observeProgress(userId, levelId)
                ) { sections, requirements, progressList ->

                    Log.d("LevelDetailVM", "Flow emit: Разделов: ${sections.size}, Требований: ${requirements.size}, Прогрессов: ${progressList.size}")

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
                Log.e("LevelDetailVM", "Ошибка загрузки данных ступени", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    // ✅ ВОССТАНОВЛЕНО: Эта функция нужна, чтобы LevelDetailScreen.kt успешно скомпилировался
    fun onRequirementAction(requirementId: String, type: String) {
        Log.d("LevelDetailVM", "onRequirementAction вызван: requirementId=$requirementId, type=$type")
        // Здесь будет логика обновления статуса требования, когда мы решим проблему с отображением
    }
}