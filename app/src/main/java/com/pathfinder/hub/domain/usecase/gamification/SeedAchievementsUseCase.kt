package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.AchievementEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import javax.inject.Inject

class SeedAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository
) {
    suspend operator fun invoke(): Result<Unit> = try {
        val achievements = listOf(
            AchievementEntity(
                id = "ach_first_step", name = "Первый шаг",
                description = "Завершить первую ступень", category = "learning",
                icon = "🎓", condition = "complete_level_1", isHidden = false
            ),
            AchievementEntity(
                id = "ach_level_master", name = "Мастер ступеней",
                description = "Завершить все 6 ступеней", category = "learning",
                icon = "🏆", condition = "complete_all_levels", isHidden = false
            ),
            AchievementEntity(
                id = "ach_first_honor", name = "Первая специализация",
                description = "Завершить первую специализацию", category = "learning",
                icon = "⭐", condition = "complete_honor_1", isHidden = false
            ),
            AchievementEntity(
                id = "ach_honor_collector", name = "Коллекционер",
                description = "Завершить 10 специализаций", category = "learning",
                icon = "📚", condition = "complete_honors_10", isHidden = false
            ),
            AchievementEntity(
                id = "ach_regular_attender", name = "Постоянный участник",
                description = "Посетить 10 событий клуба", category = "regularity",
                icon = "📅", condition = "attend_events_10", isHidden = false
            ),
            AchievementEntity(
                id = "ach_first_help", name = "Первая помощь",
                description = "Выполнить первое задание", category = "service",
                icon = "🤝", condition = "complete_task_1", isHidden = false
            ),
            AchievementEntity(
                id = "ach_team_player", name = "Командный игрок",
                description = "Участвовать в командном челлендже", category = "team",
                icon = "👥", condition = "join_team_challenge", isHidden = false
            ),
            AchievementEntity(
                id = "ach_spiritual_growth", name = "Духовный рост",
                description = "Выучить 5 стихов из Библии", category = "spiritual",
                icon = "📖", condition = "learn_verses_5", isHidden = false
            )
        )

        achievementRepository.upsertAll(achievements)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}