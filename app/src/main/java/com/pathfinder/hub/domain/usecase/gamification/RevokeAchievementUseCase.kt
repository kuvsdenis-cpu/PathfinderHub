package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import javax.inject.Inject

class RevokeAchievementUseCase @Inject constructor(private val repo: AchievementRepository) {
    suspend operator fun invoke(u: String, a: String, by: String, reason: String): Result<Unit> = try {
        repo.revoke(u, a, System.currentTimeMillis(), by, reason)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
