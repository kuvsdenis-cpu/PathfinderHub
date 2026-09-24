package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import java.util.Date
import javax.inject.Inject

class AwardAchievementUseCase @Inject constructor(private val repo: AchievementRepository) {
    suspend operator fun invoke(u: String, a: String): Result<Unit> = try {
        repo.upsertProgress(AchievementProgressEntity(
            userId = u, achievementId = a, status = "earned",
            earnedAt = Date(), revokedAt = null, revokedBy = null,
            revokeReason = null, pendingSync = true
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
