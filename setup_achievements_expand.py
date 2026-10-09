#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Расширение системы ачивок: 8 -> 32.
- 5 DAO: новые @Query методы (без изменения схемы).
- SeedAchievementsUseCase: 32 ачивки.
- CheckAchievementsUseCase: 32 условия.
- AchievementsViewModel + Screen: счётчик и группировка.
"""

import shutil
import sys
from pathlib import Path


def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта.")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"


def write_file(rel: str, content: str) -> bool:
    f = PKG / rel
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False
    shutil.copy2(f, f.with_suffix(".kt.bak"))
    f.write_text(content, encoding="utf-8")
    print(f"✅ {rel}")
    return True


def append_to_file(rel: str, before: str, insert: str, marker: str) -> bool:
    """Вставляет insert перед before. Если marker уже есть — пропускает."""
    f = PKG / rel
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False
    text = f.read_text(encoding="utf-8")
    if marker in text:
        print(f"⚠️  {rel} уже пропатчен")
        return True
    if before not in text:
        print(f"❌ {rel}: не найден якорь для вставки")
        return False
    shutil.copy2(f, f.with_suffix(".kt.bak"))
    text = text.replace(before, insert + before, 1)
    f.write_text(text, encoding="utf-8")
    print(f"✅ {rel} (патч)")
    return True


# ============================================================
# 1. Патчи DAO — новые @Query методы
# ============================================================

def patch_level_dao() -> bool:
    print("\n🔧 LevelDao.kt (+countApprovedByUser)")
    marker = "countApprovedRequirementsByUser"
    before = "    // ==================== СЧЁТЧИКИ ===================="
    insert = '''    @Query("SELECT COUNT(*) FROM level_progress WHERE userId = :userId AND status = 'approved'")
    suspend fun countApprovedRequirementsByUser(userId: String): Int

'''
    return append_to_file("data/local/dao/LevelDao.kt", before, insert, marker)


def patch_task_dao() -> bool:
    print("\n🔧 TaskDao.kt (+countCompletedByUser)")
    marker = "countCompletedByUser"
    before = "    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun upsertAssignment"
    insert = '''    @Query("SELECT COUNT(*) FROM tasks WHERE assignedTo = :userId AND status IN ('done', 'confirmed')")
    suspend fun countCompletedByUser(userId: String): Int

'''
    return append_to_file("data/local/dao/TaskDao.kt", before, insert, marker)


def patch_honor_dao() -> bool:
    print("\n🔧 HonorDao.kt (+count*ByUser для тестов, стихов, книг, требований)")
    marker = "countApprovedRequirementsByUser"
    before = "    // вњ… Р”РћР‘РђР’Р›Р•РќРћ: Р”Р»СЏ СЌРєСЂР°РЅР° РѕРґРѕР±СЂРµРЅРёСЏ РґРёСЂРµРєС‚РѕСЂР°"
    insert = '''    @Query("SELECT COUNT(*) FROM honor_progress WHERE userId = :userId AND status = 'approved'")
    suspend fun countApprovedRequirementsByUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM test_attempts WHERE userId = :userId AND passed = 1")
    suspend fun countPassedTestsByUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM verse_progress WHERE userId = :userId")
    suspend fun countVersesByUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM book_reports WHERE userId = :userId")
    suspend fun countBookReportsByUser(userId: String): Int

'''
    return append_to_file("data/local/dao/HonorDao.kt", before, insert, marker)


def patch_invite_dao() -> bool:
    print("\n🔧 InviteDao.kt (+countByCreator)")
    marker = "countByCreator"
    before = "    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun upsert(invite: InviteEntity)"
    insert = '''    @Query("SELECT COUNT(*) FROM invites WHERE createdBy = :creatorId")
    suspend fun countByCreator(creatorId: String): Int

'''
    return append_to_file("data/local/dao/InviteDao.kt", before, insert, marker)


def patch_moderation_dao() -> bool:
    print("\n🔧 ModerationDao.kt (+countUserComments)")
    marker = "countUserComments"
    before = "    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun upsertComment(c: CommentEntity)"
    insert = '''    @Query("SELECT COUNT(*) FROM comments WHERE authorId = :userId")
    suspend fun countUserComments(userId: String): Int

'''
    return append_to_file("data/local/dao/ModerationDao.kt", before, insert, marker)


# ============================================================
# 2. SeedAchievementsUseCase — 32 ачивки
# ============================================================
SEED_ACHIEVEMENTS = '''package com.pathfinder.hub.domain.usecase.gamification

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
'''


# ============================================================
# 3. CheckAchievementsUseCase — 32 условия
# ============================================================
CHECK_ACHIEVEMENTS = '''package com.pathfinder.hub.domain.usecase.gamification

import com.pathfinder.hub.data.local.dao.HonorDao
import com.pathfinder.hub.data.local.dao.InviteDao
import com.pathfinder.hub.data.local.dao.LevelDao
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.local.dao.TaskDao
import com.pathfinder.hub.data.local.dao.UserDao
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
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

        val testsDone = try { honorDao.countPassedTestsByUser(userId) } catch (_: Exception) { 0 }
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
                newlyEarned.add(achievement.id)
            }
        }

        Result.success(newlyEarned)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
'''


# ============================================================
# 4. AchievementsViewModel — счётчики
# ============================================================
ACHIEVEMENTS_VM = '''package com.pathfinder.hub.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.AchievementEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.domain.usecase.gamification.CheckAchievementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementWithProgress(
    val achievement: AchievementEntity,
    val progress: AchievementProgressEntity?
)

data class AchievementsUiState(
    val isLoading: Boolean = true,
    val achievements: List<AchievementWithProgress> = emptyList(),
    val earnedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: AchievementRepository,
    private val checkAchievements: CheckAchievementsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            // Сначала прогоняем проверки — вдруг что-то набралось с прошлого раза
            try { checkAchievements(userId) } catch (_: Exception) {}
        }

        viewModelScope.launch {
            combine(
                repository.observeAll(),
                repository.observeProgress(userId)
            ) { achievements, progressList ->
                val progressMap = progressList.associateBy { it.achievementId }
                val list = achievements.map { achievement ->
                    AchievementWithProgress(
                        achievement = achievement,
                        progress = progressMap[achievement.id]
                    )
                }.sortedWith(
                    compareByDescending<AchievementWithProgress> { it.progress?.status == "earned" }
                        .thenBy { it.achievement.category }
                        .thenBy { it.achievement.name }
                )
                val earned = list.count { it.progress?.status == "earned" }
                Triple(list, earned, list.size)
            }.collect { (list, earned, total) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        achievements = list,
                        earnedCount = earned,
                        totalCount = total
                    )
                }
            }
        }
    }
}
'''


# ============================================================
# 5. AchievementsScreen — счётчик + группировка по категориям
# ============================================================
ACHIEVEMENTS_SCREEN = '''package com.pathfinder.hub.ui.gamification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Достижения") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (state.achievements.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Достижений пока нет",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = PathfinderBlue.copy(alpha = 0.1f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Получено: ${state.earnedCount} из ${state.totalCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PathfinderBlue
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (state.totalCount == 0) 0f
                                else state.earnedCount.toFloat() / state.totalCount
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val grouped = state.achievements.groupBy { it.achievement.category }
            val categoryTitles = mapOf(
                "learning" to "Обучение",
                "requirements" to "Требования",
                "tests" to "Тесты",
                "service" to "Служение",
                "spiritual" to "Духовный рост",
                "social" to "Общение",
                "chat" to "Чат"
            )

            for ((category, items) in grouped) {
                item(key = "header_$category") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = categoryTitles[category] ?: category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PathfinderBlue
                    )
                }
                items(items, key = { it.achievement.id }) { item ->
                    AchievementCard(item)
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(item: AchievementWithProgress) {
    val achievement = item.achievement
    val isEarned = item.progress?.status == "earned"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEarned) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = achievement.icon,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEarned) PathfinderBlue else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isEarned) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Получено",
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Заблокировано",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
'''


def main():
    print("=" * 60)
    print("  Расширение ачивок: 8 -> 32")
    print("=" * 60)

    results = {
        "LevelDao.kt":                patch_level_dao(),
        "TaskDao.kt":                 patch_task_dao(),
        "HonorDao.kt":                patch_honor_dao(),
        "InviteDao.kt":               patch_invite_dao(),
        "ModerationDao.kt":           patch_moderation_dao(),
        "SeedAchievementsUseCase.kt": write_file("domain/usecase/gamification/SeedAchievementsUseCase.kt", SEED_ACHIEVEMENTS),
        "CheckAchievementsUseCase.kt": write_file("domain/usecase/gamification/CheckAchievementsUseCase.kt", CHECK_ACHIEVEMENTS),
        "AchievementsViewModel.kt":   write_file("ui/gamification/AchievementsViewModel.kt", ACHIEVEMENTS_VM),
        "AchievementsScreen.kt":      write_file("ui/gamification/AchievementsScreen.kt", ACHIEVEMENTS_SCREEN),
    }

    print("\n" + "=" * 60)
    for k, v in results.items():
        print(f"  {'✅' if v else '❌'} {k}")

    if all(results.values()):
        print("\n✅ Готово.")
        print("\nСборка: .\\gradlew assembleDebug")
        print("\nПроверь:")
        print("  1. Открой «Достижения» — 32 ачивки, разбиты на 7 категорий")
        print("  2. Сверху счётчик «X из 32»")
        print("  3. Полученные с ⭐ и голубым фоном")
        print("  4. Заблокированные с 🔒")
        print("\nЕсли упало с 'no such column' — пришли текст, поправлю имена таблиц.")
    else:
        print("\n⚠️  Смотри .bak.")
    print("=" * 60)


if __name__ == "__main__":
    main()