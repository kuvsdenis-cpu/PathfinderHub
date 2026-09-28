package com.pathfinder.hub.ui.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.domain.usecase.gamification.JoinChallengeUseCase
import com.pathfinder.hub.domain.usecase.gamification.UpdateChallengeProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengeDetailUiState(
    val isLoading: Boolean = true,
    val challenge: ChallengeEntity? = null,
    val progress: ChallengeProgressEntity? = null,
    val isJoining: Boolean = false,
    val isUpdatingProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ChallengeDetailViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository,
    private val joinChallengeUseCase: JoinChallengeUseCase,
    private val updateChallengeProgressUseCase: UpdateChallengeProgressUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ChallengeDetailUiState())
    val state: StateFlow<ChallengeDetailUiState> = _state.asStateFlow()

    fun loadChallenge(challengeId: String) {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            val clubId = "demo_club"
            achievementRepository.observeActiveChallenges(clubId).collect { challenges ->
                val challenge = challenges.find { it.id == challengeId }
                _state.update { it.copy(challenge = challenge, isLoading = false) }
            }
        }

        viewModelScope.launch {
            val allProgress = achievementRepository.observeChallengeProgress(challengeId).first()
            val userProgress = allProgress.find { it.userId == userId }
            _state.update { it.copy(progress = userProgress) }
        }
    }

    fun joinChallenge() {
        val userId = sessionManager.getUserId() ?: return
        val challengeId = _state.value.challenge?.id ?: return

        viewModelScope.launch {
            _state.update { it.copy(isJoining = true) }
            // ВАЖНО: порядок параметров — challengeId, userId
            joinChallengeUseCase(challengeId, userId)
                .onSuccess {
                    _state.update {
                        it.copy(isJoining = false, successMessage = "Вы присоединились к челленджу!")
                    }
                    loadChallenge(challengeId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isJoining = false, errorMessage = e.message) }
                }
        }
    }

    fun updateProgress(newProgress: Int) {
        val userId = sessionManager.getUserId() ?: return
        val challengeId = _state.value.challenge?.id ?: return

        viewModelScope.launch {
            _state.update { it.copy(isUpdatingProgress = true) }
            // ВАЖНО: порядок параметров — challengeId, userId, progress
            updateChallengeProgressUseCase(challengeId, userId, newProgress)
                .onSuccess {
                    _state.update {
                        it.copy(isUpdatingProgress = false, successMessage = "Прогресс обновлен!")
                    }
                    loadChallenge(challengeId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isUpdatingProgress = false, errorMessage = e.message) }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }
}