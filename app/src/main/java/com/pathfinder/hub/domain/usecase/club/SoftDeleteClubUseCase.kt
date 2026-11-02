package com.pathfinder.hub.domain.usecase.club

import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.domain.usecase.audit.LogActionUseCase
import javax.inject.Inject

/**
 * Soft delete клуба: скрывает из списков, ставит purgeAt = now + retentionDays.
 * Пишет аудит.
 */
class SoftDeleteClubUseCase @Inject constructor(
    private val clubRepo: ClubRepository,
    private val logAction: LogActionUseCase,
) {
    suspend operator fun invoke(
        clubId: String,
        clubName: String,
        userId: String,
        userRole: String = "director",
        retentionDays: Int = 90,
    ): Result<Unit> = try {
        clubRepo.softDelete(clubId, userId, retentionDays)
        logAction(
            targetType = "club",
            targetId = clubId,
            action = "club.soft_delete",
            userId = userId,
            userRole = userRole,
            clubId = clubId,
            oldValue = clubName,
            newValue = "purge after $retentionDays days",
            reason = null,
            visibleToAuthor = false,
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
