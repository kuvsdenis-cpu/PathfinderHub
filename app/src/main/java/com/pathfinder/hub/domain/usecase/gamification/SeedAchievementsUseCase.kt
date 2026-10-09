package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.entity.gamification.AchievementEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import javax.inject.Inject

/**
 * Загружает 32 ачивки в Room.
 * Категории: learning, requirements, tests, service, spiritual, social, chat.
 */
class SeedAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository
) {
    suspend operator fun invoke(): Result<Unit> = try {
        val achievements = listOf(
            // ==================== LEARNING (9) ====================
            AchievementEntity("ach_first_step", "Первый шаг",
                "Завершить первую ступень", "learning", "🎓", "complete_level_1"),
            AchievementEntity("ach_levels_3", "Путешественник",
                "Завершить 3 ступени", "learning", "🧭", "complete_levels_3"),
            AchievementEntity("ach_level_master", "Мастер ступеней",
                "Завершить все 6 ступеней", "learning", "🏆", "complete_all_levels"),
            AchievementEntity("ach_first_honor", "Первая специализация",
                "Завершить первую специализацию", "learning", "⭐", "complete_honor_1"),
            AchievementEntity("ach_honors_5", "Пятёрка",
                "Завершить 5 специализаций", "learning", "📖", "complete_honors_5"),
            AchievementEntity("ach_honor_collector", "Коллекционер",
                "Завершить 10 специализаций", "learning", "📚", "complete_honors_10"),
            AchievementEntity("ach_honors_25", "Эрудит",
                "Завершить 25 специализаций", "learning", "🎯", "complete_honors_25"),
            AchievementEntity("ach_honors_50", "Энциклопедист",
                "Завершить 50 специализаций", "learning", "🧠", "complete_honors_50"),
            AchievementEntity("ach_honors_100", "Легенда",
                "Завершить 100 специализаций", "learning", "🌟", "complete_honors_100"),

            // ==================== REQUIREMENTS (4) ====================
            AchievementEntity("ach_req_10", "Первая десятка",
                "Выполнить 10 требований", "requirements", "✍️", "complete_req_10"),
            AchievementEntity("ach_req_100", "Сотня",
                "Выполнить 100 требований", "requirements", "📝", "complete_req_100"),
            AchievementEntity("ach_req_500", "Полтысячи",
                "Выполнить 500 требований", "requirements", "🖋", "complete_req_500"),
            AchievementEntity("ach_req_1000", "Тысяча",
                "Выполнить 1000 требований", "requirements", "🏅", "complete_req_1000"),

            // ==================== TESTS (3) ====================
            AchievementEntity("ach_first_test", "Первый тест",
                "Пройти первый тест", "tests", "📋", "complete_test_1"),
            AchievementEntity("ach_tests_10", "Испытатель",
                "Пройти 10 тестов", "tests", "🧪", "complete_tests_10"),
            AchievementEntity("ach_tests_20", "Знаток",
                "Пройти 20 тестов", "tests", "🎓", "complete_tests_20"),

            // ==================== SERVICE (4) ====================
            AchievementEntity("ach_first_help", "Первая помощь",
                "Выполнить первое задание", "service", "🤝", "complete_task_1"),
            AchievementEntity("ach_tasks_10", "Работяга",
                "Выполнить 10 заданий", "service", "🛠", "complete_tasks_10"),
            AchievementEntity("ach_tasks_50", "Трудяга",
                "Выполнить 50 заданий", "service", "⚙️", "complete_tasks_50"),
            AchievementEntity("ach_tasks_100", "Мастер на все руки",
                "Выполнить 100 заданий", "service", "🔧", "complete_tasks_100"),

            // ==================== SPIRITUAL (3) ====================
            AchievementEntity("ach_verses_5", "Духовный рост",
                "Выучить 5 стихов из Библии", "spiritual", "📖", "learn_verses_5"),
            AchievementEntity("ach_verses_20", "Слово Божье",
                "Выучить 20 стихов из Библии", "spiritual", "📚", "learn_verses_20"),
            AchievementEntity("ach_books_5", "Книголюб",
                "Написать 5 отчётов о книгах", "spiritual", "📕", "learn_books_5"),

            // ==================== SOCIAL (6) ====================
            AchievementEntity("ach_invite_1", "Первый приглашённый",
                "Пригласить первого друга", "social", "👋", "invite_1"),
            AchievementEntity("ach_invite_3", "Спонсор",
                "Пригласить 3 человек", "social", "👥", "invite_3"),
            AchievementEntity("ach_invite_10", "Наставник",
                "Пригласить 10 человек", "social", "🎯", "invite_10"),
            AchievementEntity("ach_profile_complete", "Идеальный профиль",
                "Заполнить все поля профиля (включая аватар)", "social", "✅", "profile_complete"),
            AchievementEntity("ach_rep_100", "Уважение",
                "Набрать 100 очков репутации", "social", "🌟", "reputation_100"),
            AchievementEntity("ach_rep_500", "Авторитет",
                "Набрать 500 очков репутации", "social", "👑", "reputation_500"),

            // ==================== CHAT (3) ====================
            AchievementEntity("ach_chat_first", "Первое слово",
                "Отправить первое сообщение в чат", "chat", "💬", "chat_1"),
            AchievementEntity("ach_chat_50", "Общительный",
                "Отправить 50 сообщений", "chat", "🗣", "chat_50"),
            AchievementEntity("ach_chat_100", "Душа компании",
                "Отправить 100 сообщений", "chat", "🎤", "chat_100"),
        )

        achievementRepository.upsertAll(achievements)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
