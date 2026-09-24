package com.pathfinder.hub.ui.screens.home.teen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class TeenHomeUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val currentLevel: LevelEntity? = null,
    val levelProgress: Float = 0f,
    val levelProgressPercent: Int = 0,
    val totalLevels: Int = 6,
    val completedLevels: Int = 0,
    val nextEvent: EventEntity? = null,
    val tasksToday: List<TaskEntity> = emptyList(),
    val recentAchievements: List<AchievementProgressEntity> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class TeenHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val eventRepository: EventRepository,
    private val taskRepository: TaskRepository,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TeenHomeUiState())
    val state: StateFlow<TeenHomeUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        // Пользователь
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                _state.update { it.copy(user = user) }
            }
        }

        // Уровни и прогресс
        viewModelScope.launch {
            combine(
                levelRepository.observeLevels(),
                levelRepository.observeCompletions(userId)
            ) { levels, completions ->
                val completedIds = completions.map { it.levelId }.toSet()
                val nextLevel = levels.firstOrNull { it.id !in completedIds }
                nextLevel to Pair(levels.size, completions.size)
            }.collect { (nextLevel, counts) ->
                val (total, completed) = counts
                if (nextLevel != null) {
                    val progressList = levelRepository.observeProgress(userId, nextLevel.id)
                    // Разово — приблизительный прогресс, обновим отдельным сбором
                }
                _state.update {
                    it.copy(
                        currentLevel = nextLevel,
                        totalLevels = total,
                        completedLevels = completed,
                        levelProgressPercent = if (total > 0) (completed * 100) / total else 0
                    )
                }
            }
        }

        // Ближайшее событие
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            eventRepository.observeUpcoming(now, 1).collect { events ->
                _state.update { it.copy(nextEvent = events.firstOrNull()) }
            }
        }

        // Задачи на сегодня
        viewModelScope.launch {
            taskRepository.observeAssignedTasks(userId).collect { tasks ->
                val todayTasks = tasks.filter {
                    it.status != "confirmed" && it.status != "cancelled" && it.status != "rejected"
                }
                _state.update { it.copy(tasksToday = todayTasks) }
            }
        }

        // Последние ачивки
        viewModelScope.launch {
            achievementRepository.observeEarned(userId).collect { achievements ->
                _state.update {
                    it.copy(
                        recentAchievements = achievements
                            .sortedByDescending { a -> a.earnedAt?.time ?: 0 }
                            .take(3),
                        isLoading = false
                    )
                }
            }
        }
    }
}