package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.entity.gamification.ChevronItem
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.domain.usecase.gamification.CheckAchievementsUseCase
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

class CompleteLevelUseCase @Inject constructor(
    private val levelRepo: LevelRepository,
    private val achievementRepo: AchievementRepository,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) {
    suspend operator fun invoke(userId: String, levelId: String, approver: String): Result<Unit> = try {
        levelRepo.upsertCompletion(
            LevelCompletionEntity(userId = userId, levelId = levelId, completedAt = Date(), approvedBy = approver)
        )

        val currentUniform = achievementRepo.observeUniform(userId).first()
        val now = Date()
        val newChevron = ChevronItem(levelId = levelId, position = (currentUniform?.earnedChevrons?.size ?: 0) + 1, earnedAt = now)

        val updatedUniform = if (currentUniform == null) {
            DigitalUniformEntity(userId, "#1E88E5", "#FFC107", emptyList(), listOf(newChevron), emptyList(), now)
        } else {
            if (currentUniform.earnedChevrons.any { it.levelId == levelId }) currentUniform
            else currentUniform.copy(earnedChevrons = currentUniform.earnedChevrons + newChevron, updatedAt = now)
        }

        achievementRepo.upsertUniform(updatedUniform)
        checkAchievementsUseCase(userId) // <-- АВТОМАТИЧЕСКАЯ ПРОВЕРКА АЧИВОК

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}