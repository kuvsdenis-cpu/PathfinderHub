package com.pathfinder.hub.domain.usecase.gamification
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import java.util.UUID
import javax.inject.Inject

class CreateChallengeUseCase @Inject constructor(private val repo: AchievementRepository) {
    suspend operator fun invoke(c: ChallengeEntity): Result<String> = try {
        val id = c.id.ifBlank { UUID.randomUUID().toString() }
        repo.upsertChallenge(c.copy(id = id))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
