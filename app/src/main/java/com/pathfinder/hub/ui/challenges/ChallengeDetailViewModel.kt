package com.pathfinder.hub.ui.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import java.util.Date
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
    private val userRepository: UserRepository,
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
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
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
        val challenge = _state.value.challenge ?: return
        val challengeId = challenge.id

        viewModelScope.launch {
            _state.update { it.copy(isUpdatingProgress = true) }
            // ВАЖНО: порядок параметров — challengeId, userId, progress
            updateChallengeProgressUseCase(challengeId, userId, newProgress)
                .onSuccess {
                    // Достигли цели — фиксируем завершение
                    if (newProgress >= challenge.goal) {
                        markCompletedIfNeeded(challenge, userId)
                    }
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

    /**
     * Ставит completedAt, если его ещё нет.
     * НЕ выдаёт призы — это отдельная кнопка claimReward().
     */
    private suspend fun markCompletedIfNeeded(
        challenge: ChallengeEntity,
        userId: String,
    ) {
        val existing = achievementRepository.observeChallengeProgress(challenge.id).first()
            .find { it.userId == userId } ?: return
        if (existing.completedAt != null) return

        achievementRepository.upsertChallengeProgress(
            existing.copy(
                progress = challenge.goal,
                completedAt = Date(),
                rewardClaimed = false,
                pendingSync = true
            )
        )
        _state.update { it.copy(successMessage = "🏆 Челлендж завершён!") }
    }

    /**
     * Кнопка «Получить приз».
     *
     * Если в rewards лежат ID существующих ачивок — выдаём их
     * через upsertProgress. Текстовые призы показываем в снекбаре.
     */
    fun claimReward() {
        val userId = sessionManager.getUserId() ?: return
        val challenge = _state.value.challenge ?: return
        val progress = _state.value.progress ?: return
        if (progress.completedAt == null || progress.rewardClaimed) return

        viewModelScope.launch {
            try {
                // 1) Помечаем приз полученным
                achievementRepository.upsertChallengeProgress(
                    progress.copy(rewardClaimed = true, pendingSync = true)
                )

                // 2) Разделяем rewards на ачивки и текстовые
                val knownIds = achievementRepository.observeAll().first()
                    .map { it.id }.toSet()
                val (achIds, textRewards) = challenge.rewards.partition { it in knownIds }

                // 3) Выдаём ачивки напрямую
                for (achId in achIds) {
                    achievementRepository.upsertProgress(
                        AchievementProgressEntity(
                            userId = userId,
                            achievementId = achId,
                            status = "earned",
                            earnedAt = Date(),
                            revokedAt = null,
                            revokedBy = null,
                            revokeReason = null,
                            pendingSync = false
                        )
                    )
                }

                // 4) Снекбар
                val msg = buildString {
                    append("🏆 Призы получены")
                    if (textRewards.isNotEmpty()) {
                        append(": ")
                        append(textRewards.joinToString(", "))
                    }
                }
                _state.update { it.copy(successMessage = msg) }
                loadChallenge(challenge.id)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка выдачи приза") }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }
}