package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Обновление прогресса челленджа.
 *
 * completedAt и rewardClaimed СОХРАНЯЮТСЯ из существующей записи,
 * чтобы повторный апдейт не сбрасывал завершение.
 */
class UpdateChallengeProgressUseCase @Inject constructor(
    private val repo: AchievementRepository,
    private val userRepository: UserRepository,
    private val firestore: FirestoreChallengeDataSource,
) {
    suspend operator fun invoke(
        challengeId: String,
        userId: String,
        progress: Int,
    ): Result<Unit> = try {
        val existing = repo.observeChallengeProgress(challengeId).first()
            .find { it.userId == userId }

        val updated = ChallengeProgressEntity(
            challengeId = challengeId,
            userId = userId,
            progress = progress,
            completedAt = existing?.completedAt,
            rewardClaimed = existing?.rewardClaimed ?: false,
            pendingSync = true,
        )
        repo.upsertChallengeProgress(updated)

        val user = userRepository.getUser(userId)
        val clubId = user?.clubId
        if (!clubId.isNullOrBlank()) {
            firestore.upsertProgress(clubId, updated.copy(pendingSync = false))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
