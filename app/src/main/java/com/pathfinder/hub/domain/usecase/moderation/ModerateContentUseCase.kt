package com.pathfinder.hub.domain.usecase.moderation
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class ModerateContentUseCase @Inject constructor(
    private val moderationRepo: ModerationRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(
        commentId: String, moderatorId: String, approve: Boolean, reason: String? = null
    ): Result<Unit> = try {
        val s = if (approve) "published" else "hidden"
        moderationRepo.moderateComment(commentId, s, moderatorId, System.currentTimeMillis(), reason)
        auditRepo.log(AuditLogEntity(
            id = UUID.randomUUID().toString(), targetType = "comment", targetId = commentId,
            action = s, userId = moderatorId, userRole = "moderator",
            clubId = null, conferenceId = null, timestamp = Date(),
            oldValue = null, newValue = s, reason = reason,
            visibleToAuthor = true, ipHash = null, userAgent = null
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
