package com.pathfinder.hub.domain.usecase.learning
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class ApproveRequirementUseCase @Inject constructor(
    private val levelRepo: LevelRepository,
    private val honorRepo: HonorRepository,
    private val auditRepo: AuditRepository
) {
    suspend fun level(u: String, r: String, b: String, approve: Boolean, comment: String? = null): Result<Unit> = try {
        val s = if (approve) "approved" else "rejected"
        levelRepo.approveRequirement(u, r, s, b, System.currentTimeMillis())
        auditRepo.log(AuditLogEntity(
            id = UUID.randomUUID().toString(), targetType = "level_requirement",
            targetId = r, action = s, userId = b, userRole = "instructor",
            clubId = null, conferenceId = null, timestamp = Date(),
            oldValue = "submitted", newValue = s, reason = comment,
            visibleToAuthor = true, ipHash = null, userAgent = null
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun honor(u: String, r: String, b: String, approve: Boolean, comment: String? = null): Result<Unit> = try {
        val s = if (approve) "approved" else "rejected"
        honorRepo.approveRequirement(u, r, s, b, System.currentTimeMillis())
        auditRepo.log(AuditLogEntity(
            id = UUID.randomUUID().toString(), targetType = "honor_requirement",
            targetId = r, action = s, userId = b, userRole = "instructor",
            clubId = null, conferenceId = null, timestamp = Date(),
            oldValue = "submitted", newValue = s, reason = comment,
            visibleToAuthor = true, ipHash = null, userAgent = null
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
