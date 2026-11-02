package com.pathfinder.hub.domain.usecase.club

import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.audit.LogActionUseCase
import javax.inject.Inject

/**
 * Передача владения клубом существующему пользователю по email.
 * Если user не в клубе — сначала добавляется, потом передаётся.
 */
class TransferClubOwnershipByEmailUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val logAction: LogActionUseCase,
) {
    suspend operator fun invoke(
        oldOwnerId: String,
        email: String,
        clubId: String,
        userRole: String = "director",
    ): Result<Unit> {
        val result = userRepo.transferClubOwnershipByEmail(oldOwnerId, email, clubId)
        if (result.isSuccess) {
            logAction(
                targetType = "club",
                targetId = clubId,
                action = "club.transfer_ownership_by_email",
                userId = oldOwnerId,
                userRole = userRole,
                clubId = clubId,
                oldValue = oldOwnerId,
                newValue = email,
                reason = null,
                visibleToAuthor = false,
            )
        }
        return result
    }
}
