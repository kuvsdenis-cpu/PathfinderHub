package com.pathfinder.hub.domain.usecase.moderation
import com.pathfinder.hub.data.repository.ModerationRepository
import javax.inject.Inject

class EscalateModerationUseCase @Inject constructor(private val moderationRepo: ModerationRepository) {
    suspend operator fun invoke(requestId: String, level: Int, to: String): Result<Unit> = try {
        moderationRepo.escalate(requestId, level, to, System.currentTimeMillis())
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
