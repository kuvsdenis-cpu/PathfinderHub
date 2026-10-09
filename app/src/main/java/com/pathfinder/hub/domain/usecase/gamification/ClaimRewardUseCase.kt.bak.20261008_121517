package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

/**
 * Пометка приза полученным + выдача ачивок из rewards.
 */
class ClaimRewardUseCase @Inject constructor(
    private val repo: AchievementRepository,
    private val userRepository: UserRepository,
    private val firestore: FirestoreChallengeDataSource,
) {
    /**
     * Возвращает: (achievementIds, textRewards) — что выдали.
     */
    suspend operator fun invoke(
        challengeId: String,
        userId: String,
    ): Result<Pair<List<String>, List<String>>> {
        return try {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId ?: ""

            val challenge = repo.observeActiveChallenges(clubId).first()
                .find { it.id == challengeId }
                ?: return Result.failure(IllegalArgumentException("Челлендж не найден"))

            val existing = repo.observeChallengeProgress(challengeId).first()
                .find { it.userId == userId }
                ?: return Result.failure(IllegalStateException("Нет записи прогресса"))

            if (existing.rewardClaimed) {
                return Result.success(emptyList<String>() to emptyList())
            }

            val updated = existing.copy(rewardClaimed = true, pendingSync = true)
            repo.upsertChallengeProgress(updated)
            if (clubId.isNotBlank()) {
                firestore.upsertProgress(clubId, updated.copy(pendingSync = false))
            }

            val knownIds = repo.observeAll().first().map { it.id }.toSet()
            val (achIds, textRewards) = challenge.rewards.partition { it in knownIds }

            for (achId in achIds) {
                repo.upsertProgress(
                    AchievementProgressEntity(
                        userId = userId,
                        achievementId = achId,
                        status = "earned",
                        earnedAt = Date(),
                        revokedAt = null,
                        revokedBy = null,
                        revokeReason = null,
                        pendingSync = false,
                    )
                )
            }

            Result.success(achIds to textRewards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
