package com.pathfinder.hub.domain.usecase.auth
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.SecurityLogEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class LogoutUseCase @Inject constructor(private val auditRepo: AuditRepository) {
    suspend operator fun invoke(userId: String): Result<Unit> = try {
        auditRepo.logSecurity(SecurityLogEntity(
            id = UUID.randomUUID().toString(), userId = userId, action = "logout",
            ipHash = "", userAgent = "android", timestamp = Date(), success = true, details = null
        ))
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
