package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

class CheckAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository
) {
    suspend operator fun invoke(userId: String): Result<List<String>> = try {
        val newlyEarned = mutableListOf<String>()

        val allAchievements = achievementRepository.observeAll().first()
        val userProgress = achievementRepository.observeProgress(userId).first()
        val earnedIds = userProgress.filter { it.status == "earned" }.map { it.achievementId }.toSet()

        val completedLevels = levelRepository.observeCompletions(userId).first()
        val completedHonors = honorRepository.observeCompletions(userId).first()

        for (achievement in allAchievements) {
            if (achievement.id in earnedIds) continue

            val conditionMet = when (achievement.condition) {
                "complete_level_1" -> completedLevels.size >= 1
                "complete_all_levels" -> completedLevels.size >= 6
                "complete_honor_1" -> completedHonors.size >= 1
                "complete_honors_10" -> completedHonors.size >= 10
                else -> false // Остальные условия можно добавить по мере развития
            }

            if (conditionMet) {
                val progress = AchievementProgressEntity(
                    userId = userId,
                    achievementId = achievement.id,
                    status = "earned",
                    earnedAt = Date(),
                    revokedAt = null,
                    revokedBy = null,
                    revokeReason = null,
                    pendingSync = false
                )
                achievementRepository.upsertProgress(progress)
                newlyEarned.add(achievement.id)
            }
        }

        Result.success(newlyEarned)
    } catch (e: Exception) {
        Result.failure(e)
    }
}