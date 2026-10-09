package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.remote.FirestoreChallengeDataSource
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import javax.inject.Inject

/**
 * Присоединение к челленджу.
 *
 * Пишем в Room (AchievementRepository), затем в Firestore.
 * Если Firestore упал — только Room, SyncWorker повторит.
 */
class JoinChallengeUseCase @Inject constructor(
    private val repo: AchievementRepository,
    private val userRepository: UserRepository,
    private val firestore: FirestoreChallengeDataSource,
) {
    suspend operator fun invoke(challengeId: String, userId: String): Result<Unit> = try {
        repo.upsertChallengeProgress(
            ChallengeProgressEntity(
                challengeId = challengeId,
                userId = userId,
                progress = 0,
                completedAt = null,
                rewardClaimed = false,
                pendingSync = true,
            )
        )

        val user = userRepository.getUser(userId)
        val clubId = user?.clubId
        if (!clubId.isNullOrBlank()) {
            firestore.upsertProgress(
                clubId,
                ChallengeProgressEntity(
                    challengeId = challengeId,
                    userId = userId,
                    progress = 0,
                    completedAt = null,
                    rewardClaimed = false,
                    pendingSync = false,
                )
            )
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
