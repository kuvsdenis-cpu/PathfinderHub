package com.pathfinder.hub.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.AchievementEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.domain.usecase.gamification.CheckAchievementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementWithProgress(
    val achievement: AchievementEntity,
    val progress: AchievementProgressEntity?
)

data class AchievementsUiState(
    val isLoading: Boolean = true,
    val achievements: List<AchievementWithProgress> = emptyList(),
    val earnedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: AchievementRepository,
    private val checkAchievements: CheckAchievementsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            // Сначала прогоняем проверки — вдруг что-то набралось с прошлого раза
            try { checkAchievements(userId) } catch (_: Exception) {}
        }

        viewModelScope.launch {
            combine(
                repository.observeAll(),
                repository.observeProgress(userId)
            ) { achievements, progressList ->
                val progressMap = progressList.associateBy { it.achievementId }
                val list = achievements.map { achievement ->
                    AchievementWithProgress(
                        achievement = achievement,
                        progress = progressMap[achievement.id]
                    )
                }.sortedWith(
                    compareByDescending<AchievementWithProgress> { it.progress?.status == "earned" }
                        .thenBy { it.achievement.category }
                        .thenBy { it.achievement.name }
                )
                val earned = list.count { it.progress?.status == "earned" }
                Triple(list, earned, list.size)
            }.collect { (list, earned, total) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        achievements = list,
                        earnedCount = earned,
                        totalCount = total
                    )
                }
            }
        }
    }
}
