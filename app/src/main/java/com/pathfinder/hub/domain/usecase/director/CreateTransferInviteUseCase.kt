package com.pathfinder.hub.domain.usecase.director

import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.domain.usecase.audit.LogActionUseCase
import javax.inject.Inject

/**
 * Создание transfer-invite — приглашение нового директора с передачей клуба.
 * Пишет audit log.
 */
class CreateTransferInviteUseCase @Inject constructor(
    private val inviteRepo: InviteRepository,
    private val logAction: LogActionUseCase,
) {
    suspend operator fun invoke(
        clubId: String,
        createdBy: String,
        userRole: String = "director",
        lifetimeDays: Int = 7,
    ): Result<InviteEntity> = try {
        val invite = inviteRepo.createTransferInvite(
            clubId = clubId,
            createdBy = createdBy,
            lifetimeDays = lifetimeDays,
        )
        logAction(
            targetType = "club",
            targetId = clubId,
            action = "club.transfer_invite_created",
            userId = createdBy,
            userRole = userRole,
            clubId = clubId,
            oldValue = null,
            newValue = invite.code,
            reason = null,
            visibleToAuthor = false,
        )
        Result.success(invite)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
