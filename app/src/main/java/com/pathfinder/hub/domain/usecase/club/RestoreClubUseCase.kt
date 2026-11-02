package com.pathfinder.hub.domain.usecase.club

import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.domain.usecase.audit.LogActionUseCase
import javax.inject.Inject

/**
 * Восстановление клуба из корзины.
 * Пишет аудит.
 */
class RestoreClubUseCase @Inject constructor(
    private val clubRepo: ClubRepository,
    private val logAction: LogActionUseCase,
) {
    suspend operator fun invoke(
        clubId: String,
        clubName: String,
        userId: String,
        userRole: String = "director",
    ): Result<Unit> = try {
        clubRepo.restore(clubId, userId)
        logAction(
            targetType = "club",
            targetId = clubId,
            action = "club.restore",
            userId = userId,
            userRole = userRole,
            clubId = clubId,
            oldValue = null,
            newValue = clubName,
            reason = null,
            visibleToAuthor = false,
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
