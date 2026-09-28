package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.entity.gamification.BadgeItem
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.domain.usecase.gamification.CheckAchievementsUseCase
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

class CompleteHonorUseCase @Inject constructor(
    private val honorRepo: HonorRepository,
    private val achievementRepo: AchievementRepository,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) {
    suspend operator fun invoke(userId: String, honorId: String, approver: String): Result<Unit> = try {
        honorRepo.upsertCompletion(
            HonorCompletionEntity(userId = userId, honorId = honorId, completedAt = Date(), approvedBy = approver)
        )

        val currentUniform = achievementRepo.observeUniform(userId).first()
        val now = Date()
        val newBadge = BadgeItem(honorId = honorId, position = (currentUniform?.earnedBadges?.size ?: 0) + 1, earnedAt = now)

        val updatedUniform = if (currentUniform == null) {
            DigitalUniformEntity(userId, "#1E88E5", "#FFC107", listOf(newBadge), emptyList(), emptyList(), now)
        } else {
            if (currentUniform.earnedBadges.any { it.honorId == honorId }) currentUniform
            else currentUniform.copy(earnedBadges = currentUniform.earnedBadges + newBadge, updatedAt = now)
        }

        achievementRepo.upsertUniform(updatedUniform)
        checkAchievementsUseCase(userId) // <-- АВТОМАТИЧЕСКАЯ ПРОВЕРКА АЧИВОК

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}