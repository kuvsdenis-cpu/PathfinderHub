package com.pathfinder.hub.ui.screens.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LevelUiModel(
    val level: LevelEntity,
    val isCompleted: Boolean,
    val isCurrent: Boolean,
    val isLocked: Boolean,
    val approvedCount: Int,
    val totalCount: Int,
    val progressPercent: Int
)

data class MyLevelsUiState(
    val isLoading: Boolean = true,
    val levels: List<LevelUiModel> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class MyLevelsViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(MyLevelsUiState())
    val state: StateFlow<MyLevelsUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет активной сессии") }
            return
        }

        viewModelScope.launch {
            combine(
                levelRepository.observeLevels(),
                levelRepository.observeCompletions(userId)
            ) { levels, completions ->
                levels to completions
            }.collect { (levels, completions) ->
                if (levels.isEmpty()) {
                    _state.update { it.copy(isLoading = false, levels = emptyList()) }
                    return@collect
                }

                val completedIds = completions.map { it.levelId }.toSet()
                val sortedLevels = levels.sortedBy { it.order }

                // Находим текущую ступень — первую незавершённую
                val currentLevelId = sortedLevels
                    .firstOrNull { it.id !in completedIds }
                    ?.id

                val uiModels = sortedLevels.map { level ->
                    val isCompleted = level.id in completedIds
                    val isCurrent = level.id == currentLevelId
                    val isLocked = currentLevelId?.let { current ->
                        val currentOrder = sortedLevels.firstOrNull { it.id == current }?.order ?: 0
                        level.order > currentOrder
                    } ?: false

                    // Загружаем прогресс по каждому уровню
                    val progress = loadProgressForLevel(userId, level.id)
                    val total = progress.size
                    val approved = progress.count { it.status == "approved" }
                    val percent = if (total > 0) (approved * 100) / total else 0

                    LevelUiModel(
                        level = level,
                        isCompleted = isCompleted,
                        isCurrent = isCurrent,
                        isLocked = isLocked,
                        approvedCount = approved,
                        totalCount = total,
                        progressPercent = percent
                    )
                }

                _state.update { it.copy(isLoading = false, levels = uiModels) }
            }
        }
    }

    private suspend fun loadProgressForLevel(
        userId: String,
        levelId: String
    ): List<LevelProgressEntity> {
        return levelRepository.getProgressSnapshot(userId, levelId)
    }
}