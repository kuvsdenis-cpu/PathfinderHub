package com.pathfinder.hub.domain.usecase.auth
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.local.entity.core.UserEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class RegisterByInviteUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val inviteRepo: InviteRepository
) {
    suspend operator fun invoke(
        inviteCode: String, firstName: String, lastName: String,
        email: String, birthDate: Date
    ): Result<String> = try {
        val invite = inviteRepo.getByCode(inviteCode)
            ?: return Result.failure(Exception("Invalid invite"))
        val userId = UUID.randomUUID().toString()
        userRepo.upsert(UserEntity(
            id = userId, email = email, emailVerified = false,
            firstName = firstName, lastName = lastName, role = invite.role,
            clubId = invite.clubId, conferenceId = null, birthDate = birthDate,
            avatar = null, parentEmail = null,
            parentalConsentStatus = if (invite.role == "teen") "pending" else "approved",
            parentalConsentRequestedAt = null, parentalConsentApprovedAt = null,
            accessLevel = if (invite.role == "teen") "limited" else "full",
            status = "active", deletionRequestedBy = null, deletionRequestedAt = null,
            anonymizedAt = null, permanentDeletionAt = null,
            onboardingCompleted = false, createdAt = Date(), lastLoginAt = null
        ))
        inviteRepo.markUsed(inviteCode, "used", userId, System.currentTimeMillis())
        Result.success(userId)
    } catch (e: Exception) { Result.failure(e) }
}
