package com.pathfinder.hub.domain.usecase.auth
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.SecurityLogEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<String> = try {
        val user = userRepo.getUserByEmail(email) ?: return Result.failure(Exception("User not found"))
        userRepo.update(user.copy(lastLoginAt = Date()))
        auditRepo.logSecurity(SecurityLogEntity(
            id = UUID.randomUUID().toString(), userId = user.id, action = "login",
            ipHash = "", userAgent = "android", timestamp = Date(), success = true, details = null
        ))
        Result.success(user.id)
    } catch (e: Exception) { Result.failure(e) }
}
