package com.pathfinder.hub.domain.usecase.audit
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class LogActionUseCase @Inject constructor(private val repo: AuditRepository) {
    suspend operator fun invoke(
        targetType: String, targetId: String, action: String,
        userId: String, userRole: String, clubId: String?,
        oldValue: String? = null, newValue: String? = null,
        reason: String? = null, visibleToAuthor: Boolean = true
    ): Result<String> = try {
        val id = UUID.randomUUID().toString()
        repo.log(AuditLogEntity(
            id = id, targetType = targetType, targetId = targetId,
            action = action, userId = userId, userRole = userRole,
            clubId = clubId, conferenceId = null, timestamp = Date(),
            oldValue = oldValue, newValue = newValue, reason = reason,
            visibleToAuthor = visibleToAuthor, ipHash = null, userAgent = null
        ))
        Result.success(id)
    } catch (e: Exception) { Result.failure(e) }
}
