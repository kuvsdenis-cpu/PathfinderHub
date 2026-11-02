package com.pathfinder.hub.domain.usecase.club

import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.audit.LogActionUseCase
import javax.inject.Inject

/**
 * Передача владения клубом существующему сотруднику.
 *
 * Правила:
 *  - Старый owner теряет isOwnerClub и выпадает из clubIds.
 *  - Новый owner получает role = director, isOwnerClub = true, clubIds += clubId.
 *  - Пишем audit log.
 */
class TransferClubOwnershipUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val logAction: LogActionUseCase,
) {
    suspend operator fun invoke(
        oldOwnerId: String,
        newOwnerId: String,
        clubId: String,
        clubName: String = "",
        userRole: String = "director",
    ): Result<Unit> {
        val result = userRepo.transferClubOwnership(oldOwnerId, newOwnerId, clubId)
        if (result.isSuccess) {
            logAction(
                targetType = "club",
                targetId = clubId,
                action = "club.transfer_ownership",
                userId = oldOwnerId,
                userRole = userRole,
                clubId = clubId,
                oldValue = oldOwnerId,
                newValue = newOwnerId,
                reason = clubName.ifBlank { null },
                visibleToAuthor = false,
            )
        }
        return result
    }
}
