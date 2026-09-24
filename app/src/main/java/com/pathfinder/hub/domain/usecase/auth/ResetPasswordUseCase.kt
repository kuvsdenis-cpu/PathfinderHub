package com.pathfinder.hub.domain.usecase.auth
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.auth.TemporaryPasswordEntity
import com.pathfinder.hub.data.local.entity.audit.SecurityLogEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(userId: String, directorId: String): Result<String> = try {
        val tempPassword = UUID.randomUUID().toString().take(8)
        auditRepo.logSecurity(SecurityLogEntity(
            id = UUID.randomUUID().toString(), userId = userId,
            action = "password_reset_by_director", ipHash = "",
            userAgent = "android", timestamp = Date(), success = true, details = directorId
        ))
        Result.success(tempPassword)
    } catch (e: Exception) { Result.failure(e) }
}
