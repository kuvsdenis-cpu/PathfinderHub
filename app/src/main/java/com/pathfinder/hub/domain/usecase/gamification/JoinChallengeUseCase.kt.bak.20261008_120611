package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import javax.inject.Inject

class JoinChallengeUseCase @Inject constructor(private val repo: AchievementRepository) {
    suspend operator fun invoke(challengeId: String, userId: String): Result<Unit> = try {
        repo.upsertChallengeProgress(ChallengeProgressEntity(
            challengeId = challengeId, userId = userId, progress = 0,
            completedAt = null, rewardClaimed = false, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
