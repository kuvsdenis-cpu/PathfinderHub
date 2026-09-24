package com.pathfinder.hub.domain.usecase.moderation
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.local.entity.planning.AppealEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class SubmitAppealUseCase @Inject constructor(private val moderationRepo: ModerationRepository) {
    suspend operator fun invoke(
        moderationRequestId: String, authorId: String, text: String
    ): Result<String> = try {
        val id = UUID.randomUUID().toString()
        moderationRepo.upsertAppeal(AppealEntity(
            id = id, moderationRequestId = moderationRequestId, authorId = authorId,
            text = text, status = "pending", reviewedBy = null,
            reviewedAt = null, createdAt = Date(), pendingSync = true
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
