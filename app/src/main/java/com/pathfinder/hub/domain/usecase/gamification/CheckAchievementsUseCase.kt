package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.dao.HonorDao
import com.pathfinder.hub.data.local.dao.InviteDao
import com.pathfinder.hub.data.local.dao.LevelDao
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.local.dao.TaskDao
import com.pathfinder.hub.data.local.dao.TestDao
import com.pathfinder.hub.data.local.dao.UserDao
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.local.entity.gamification.SpecialMarkItem
import com.pathfinder.hub.data.local.entity.gamification.BadgeItem
import com.pathfinder.hub.data.local.entity.gamification.ChevronItem
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

/**
 * Проверяет все 32 ачивки.
 *
 * Вызывается:
 *   - из CompleteLevelUseCase / CompleteHonorUseCase (автоматически)
 *   - из AchievementsViewModel при открытии экрана
 */
class CheckAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val levelDao: LevelDao,
    private val honorDao: HonorDao,
    private val testDao: TestDao,
    private val taskDao: TaskDao,
    private val inviteDao: InviteDao,
    private val moderationDao: ModerationDao,
    private val userDao: UserDao
) {
    suspend operator fun invoke(userId: String): Result<List<String>> = try {
        val newlyEarned = mutableListOf<String>()

        val allAchievements = achievementRepository.observeAll().first()
        val userProgress = achievementRepository.observeProgress(userId).first()
        val earnedIds = userProgress.filter { it.status == "earned" }
            .map { it.achievementId }.toSet()

        // ---- Данные ----
        val levelsDone = levelRepository.observeCompletions(userId).first().size
        val honorsDone = honorRepository.observeCompletions(userId).first().size

        val levelReqDone = try { levelDao.countApprovedRequirementsByUser(userId) } catch (_: Exception) { 0 }
        val honorReqDone = try { honorDao.countApprovedRequirementsByUser(userId) } catch (_: Exception) { 0 }
        val totalReqDone = levelReqDone + honorReqDone

        val testsDone = try { testDao.countPassedTestsByUser(userId) } catch (_: Exception) { 0 }
        val versesDone = try { honorDao.countVersesByUser(userId) } catch (_: Exception) { 0 }
        val booksDone = try { honorDao.countBookReportsByUser(userId) } catch (_: Exception) { 0 }
        val tasksDone = try { taskDao.countCompletedByUser(userId) } catch (_: Exception) { 0 }
        val invitesDone = try { inviteDao.countByCreator(userId) } catch (_: Exception) { 0 }
        val chatMsgs = try { moderationDao.countUserComments(userId) } catch (_: Exception) { 0 }

        val reputation = try {
            achievementRepository.observeReputation(userId).first()?.personal ?: 0
        } catch (_: Exception) { 0 }

        val user = try { userDao.getUser(userId) } catch (_: Exception) { null }
        val profileComplete = user != null &&
                user.firstName.isNotBlank() &&
                user.lastName.isNotBlank() &&
                !user.avatar.isNullOrBlank() &&
                user.emailVerified

        // ---- Проверка ----
        for (achievement in allAchievements) {
            if (achievement.id in earnedIds) continue

            val met = when (achievement.condition) {
                // learning
                "complete_level_1" -> levelsDone >= 1
                "complete_levels_3" -> levelsDone >= 3
                "complete_all_levels" -> levelsDone >= 6
                "complete_honor_1" -> honorsDone >= 1
                "complete_honors_5" -> honorsDone >= 5
                "complete_honors_10" -> honorsDone >= 10
                "complete_honors_25" -> honorsDone >= 25
                "complete_honors_50" -> honorsDone >= 50
                "complete_honors_100" -> honorsDone >= 100

                // requirements
                "complete_req_10" -> totalReqDone >= 10
                "complete_req_100" -> totalReqDone >= 100
                "complete_req_500" -> totalReqDone >= 500
                "complete_req_1000" -> totalReqDone >= 1000

                // tests
                "complete_test_1" -> testsDone >= 1
                "complete_tests_10" -> testsDone >= 10
                "complete_tests_20" -> testsDone >= 20

                // service
                "complete_task_1" -> tasksDone >= 1
                "complete_tasks_10" -> tasksDone >= 10
                "complete_tasks_50" -> tasksDone >= 50
                "complete_tasks_100" -> tasksDone >= 100

                // spiritual
                "learn_verses_5" -> versesDone >= 5
                "learn_verses_20" -> versesDone >= 20
                "learn_books_5" -> booksDone >= 5

                // social
                "invite_1" -> invitesDone >= 1
                "invite_3" -> invitesDone >= 3
                "invite_10" -> invitesDone >= 10
                "profile_complete" -> profileComplete
                "reputation_100" -> reputation >= 100
                "reputation_500" -> reputation >= 500

                // chat
                "chat_1" -> chatMsgs >= 1
                "chat_50" -> chatMsgs >= 50
                "chat_100" -> chatMsgs >= 100

                else -> false
            }

            if (met) {
                achievementRepository.upsertProgress(
                    AchievementProgressEntity(
                        userId = userId,
                        achievementId = achievement.id,
                        status = "earned",
                        earnedAt = Date(),
                        revokedAt = null,
                        revokedBy = null,
                        revokeReason = null,
                        pendingSync = false
                    )
                )
                addSpecialMarkToUniform(userId, achievement.id, achievement.icon)
                newlyEarned.add(achievement.id)
            }
        }

        Result.success(newlyEarned)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun addSpecialMarkToUniform(userId: String, achievementId: String, icon: String) {
        try {
            val current = achievementRepository.getUniform(userId)
            val now = Date()
            val newMark = SpecialMarkItem(achievementId = achievementId, icon = icon, earnedAt = now)
            val updated = if (current == null) {
                DigitalUniformEntity(
                    userId = userId,
                    shirtColor = "#1E88E5",
                    tieColor = "#FFC107",
                    earnedBadges = emptyList(),
                    earnedChevrons = emptyList(),
                    specialMarks = listOf(newMark),
                    updatedAt = now,
                )
            } else {
                if (current.specialMarks.any { it.achievementId == achievementId }) current
                else current.copy(specialMarks = current.specialMarks + newMark, updatedAt = now)
            }
            achievementRepository.upsertUniformWithSync(updated)
        } catch (_: Exception) {}
    }
    /**
     * Восстанавливает digital_uniforms, если строки нет.
     * Собирает нашивки из honor_completions, шевроны из level_completions,
     * особые знаки из achievement_progress (status='earned').
     * Идемпотентно: если строка уже есть — возвращает её без изменений.
     */
    suspend fun rebuildUniformIfMissing(userId: String): DigitalUniformEntity? {
        return try {
            achievementRepository.getUniform(userId)?.let { return it }

            val levelCompletions = try {
                levelRepository.observeCompletions(userId).first()
            } catch (_: Exception) { emptyList() }
            val honorCompletions = try {
                honorRepository.observeCompletions(userId).first()
            } catch (_: Exception) { emptyList() }
            val earnedProgress = try {
                achievementRepository.observeProgress(userId).first().filter { it.status == "earned" }
            } catch (_: Exception) { emptyList() }
            val allAchievements = try {
                achievementRepository.observeAll().first().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }

            val now = Date()
            val badges = honorCompletions
                .distinctBy { it.honorId }
                .sortedBy { it.completedAt }
                .mapIndexed { i, h -> BadgeItem(honorId = h.honorId, position = i, earnedAt = h.completedAt) }
            val chevrons = levelCompletions
                .distinctBy { it.levelId }
                .sortedBy { it.completedAt }
                .mapIndexed { i, l -> ChevronItem(levelId = l.levelId, position = i, earnedAt = l.completedAt) }
            val marks = earnedProgress
                .distinctBy { it.achievementId }
                .mapNotNull { p ->
                    val at = p.earnedAt ?: return@mapNotNull null
                    SpecialMarkItem(
                        achievementId = p.achievementId,
                        icon = allAchievements[p.achievementId]?.icon ?: "",
                        earnedAt = at,
                    )
                }

            val uniform = DigitalUniformEntity(
                userId = userId,
                shirtColor = "#1E88E5",
                tieColor = "#FFC107",
                earnedBadges = badges,
                earnedChevrons = chevrons,
                specialMarks = marks,
                updatedAt = now,
            )
            achievementRepository.upsertUniformWithSync(uniform)
            uniform
        } catch (_: Exception) {
            null
        }
    }

}

