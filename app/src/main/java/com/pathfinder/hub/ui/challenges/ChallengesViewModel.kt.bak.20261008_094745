package com.pathfinder.hub.ui.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengeWithProgress(
    val challenge: ChallengeEntity,
    val progress: ChallengeProgressEntity?
)

data class ChallengesUiState(
    val isLoading: Boolean = true,
    val challenges: List<ChallengeWithProgress> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChallengesUiState())
    val state: StateFlow<ChallengesUiState> = _state.asStateFlow()

    init {
        loadChallenges()
    }

    private fun loadChallenges() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            val clubId = "demo_club"
            achievementRepository.observeActiveChallenges(clubId).collect { challenges ->
                // Для каждого челленджа получаем прогресс текущего пользователя
                val challengesWithProgress = challenges.map { challenge ->
                    val allProgress = achievementRepository.observeChallengeProgress(challenge.id).first()
                    val userProgress = allProgress.find { it.userId == userId }
                    ChallengeWithProgress(challenge = challenge, progress = userProgress)
                }
                _state.update {
                    it.copy(isLoading = false, challenges = challengesWithProgress)
                }
            }
        }
    }
}