package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import javax.inject.Inject

class UpdateChallengeProgressUseCase @Inject constructor(private val repo: AchievementRepository) {
    suspend operator fun invoke(challengeId: String, userId: String, progress: Int): Result<Unit> = try {
        repo.upsertChallengeProgress(ChallengeProgressEntity(
            challengeId = challengeId, userId = userId, progress = progress,
            completedAt = null, rewardClaimed = false, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
