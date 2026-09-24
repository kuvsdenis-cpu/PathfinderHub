#!/usr/bin/env python3
"""
Обновление общих файлов Pathfinder Hub:
- Routes.kt
- PathfinderNavHost.kt
- LevelDao.kt
- LevelRepository.kt
- HonorDao.kt
- HonorRepository.kt

Запуск: python parser/update_navigation.py
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = []

def add(path, content):
    FILES.append((path, content.strip() + "\n"))

def write_all():
    for path, content in FILES:
        full = ROOT / path
        full.parent.mkdir(parents=True, exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"OK {path}")
    print(f"\nГотово. Обновлено файлов: {len(FILES)}")

# ============================================================
# 1. Routes.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt",
    '''
package com.pathfinder.hub.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val INVITE = "invite"
    const val REGISTER = "register"
    const val ONBOARDING = "onboarding"

    const val TEEN_HOME = "teen_home"
    const val PARENT_HOME = "parent_home"
    const val INSTRUCTOR_HOME = "instructor_home"
    const val DIRECTOR_HOME = "director_home"
    const val SECRETARY_HOME = "secretary_home"
    const val CONFERENCE_HOME = "conference_home"

    const val DIRECTOR_INVITES = "director_invites"

    const val MY_LEVELS = "my_levels"
    const val LEVEL_DETAIL = "level_detail"
    const val REQUIREMENT_DETAIL = "requirement_detail"

    const val HONOR_CATALOG = "honor_catalog"
    const val HONOR_DETAIL = "honor_detail"
    const val HONOR_REQUIREMENT = "honor_requirement"
    const val HONOR_TEST = "honor_test"
    const val HONOR_REPORT = "honor_report"
    const val MY_HONORS = "my_honors"

    const val EVENTS = "events"
    const val EVENT_DETAIL = "event_detail"

    const val MY_TASKS = "my_tasks"

    const val TEEN_PROFILE = "teen_profile"
}
''')

# ============================================================
# 2. PathfinderNavHost.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt",
    '''
package com.pathfinder.hub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pathfinder.hub.ui.screens.auth.LoginScreen
import com.pathfinder.hub.ui.screens.auth.RegisterScreen
import com.pathfinder.hub.ui.screens.events.EventDetailScreen
import com.pathfinder.hub.ui.screens.events.EventsScreen
import com.pathfinder.hub.ui.screens.honors.HonorCatalogScreen
import com.pathfinder.hub.ui.screens.honors.HonorDetailScreen
import com.pathfinder.hub.ui.screens.honors.HonorRequirementDetailScreen
import com.pathfinder.hub.ui.screens.honors.MyHonorsScreen
import com.pathfinder.hub.ui.screens.honors.ReportUploadScreen
import com.pathfinder.hub.ui.screens.honors.TestScreen
import com.pathfinder.hub.ui.screens.home.RoleHomeScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorInvitesScreen
import com.pathfinder.hub.ui.screens.home.teen.TeenHomeScreen
import com.pathfinder.hub.ui.screens.levels.LevelDetailScreen
import com.pathfinder.hub.ui.screens.levels.MyLevelsScreen
import com.pathfinder.hub.ui.screens.levels.RequirementDetailScreen
import com.pathfinder.hub.ui.screens.onboarding.OnboardingScreen
import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen
import com.pathfinder.hub.ui.screens.tasks.MyTasksScreen

@Composable
fun PathfinderNavHost(
    navController: NavHostController,
    startDestination: String = Routes.LOGIN
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // AUTH
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { role ->
                    navController.navigate(routeForRole(role)) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToInvite = { navController.navigate(Routes.REGISTER) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("${Routes.ONBOARDING}/teen/demo_user") {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("${Routes.ONBOARDING}/{role}/{userId}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "teen"
            val userId = backStackEntry.arguments?.getString("userId") ?: "demo"
            OnboardingScreen(
                role = role,
                userId = userId,
                onFinish = {
                    navController.navigate(routeForRole(role)) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // HOME BY ROLE
        composable(Routes.TEEN_HOME) {
            TeenHomeScreen(
                onNavigateToLevels = { navController.navigate(Routes.MY_LEVELS) },
                onNavigateToHonors = { navController.navigate(Routes.HONOR_CATALOG) },
                onNavigateToMyHonors = { navController.navigate(Routes.MY_HONORS) },
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToTasks = { navController.navigate(Routes.MY_TASKS) },
                onNavigateToProfile = { navController.navigate(Routes.TEEN_PROFILE) }
            )
        }
        composable(Routes.PARENT_HOME) { RoleHomeScreen(role = "Родитель") }
        composable(Routes.INSTRUCTOR_HOME) { RoleHomeScreen(role = "Наставник") }
        composable(Routes.DIRECTOR_HOME) {
            RoleHomeScreen(
                role = "Директор клуба",
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) }
            )
        }
        composable(Routes.SECRETARY_HOME) { RoleHomeScreen(role = "Секретарь клуба") }
        composable(Routes.CONFERENCE_HOME) { RoleHomeScreen(role = "Конференция") }

        // DIRECTOR
        composable(Routes.DIRECTOR_INVITES) {
            DirectorInvitesScreen(onBack = { navController.popBackStack() })
        }

        // TEEN: СТУПЕНИ
        composable(Routes.MY_LEVELS) {
            MyLevelsScreen(
                onBack = { navController.popBackStack() },
                onOpenLevel = { levelId ->
                    navController.navigate("${Routes.LEVEL_DETAIL}/$levelId")
                }
            )
        }

        composable("${Routes.LEVEL_DETAIL}/{levelId}") {
            LevelDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { lvlId, reqId ->
                    navController.navigate("${Routes.REQUIREMENT_DETAIL}/$lvlId/$reqId")
                }
            )
        }

        composable("${Routes.REQUIREMENT_DETAIL}/{levelId}/{requirementId}") {
            RequirementDetailScreen(onBack = { navController.popBackStack() })
        }

        // TEEN: СПЕЦИАЛИЗАЦИИ
        composable(Routes.HONOR_CATALOG) {
            HonorCatalogScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId ->
                    navController.navigate("${Routes.HONOR_DETAIL}/$honorId")
                }
            )
        }

        composable("${Routes.HONOR_DETAIL}/{honorId}") {
            HonorDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { hId, rId ->
                    navController.navigate("${Routes.HONOR_REQUIREMENT}/$hId/$rId")
                }
            )
        }

        composable("${Routes.HONOR_REQUIREMENT}/{honorId}/{requirementId}") { entry ->
            val hId = entry.arguments?.getString("honorId") ?: ""
            val rId = entry.arguments?.getString("requirementId") ?: ""
            HonorRequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenTest = {
                    navController.navigate("${Routes.HONOR_TEST}/$hId")
                },
                onOpenReport = {
                    navController.navigate("${Routes.HONOR_REPORT}/$hId/$rId")
                }
            )
        }

        composable("${Routes.HONOR_TEST}/{honorId}") {
            TestScreen(
                onBack = { navController.popBackStack() },
                onFinished = { navController.popBackStack() }
            )
        }

        composable("${Routes.HONOR_REPORT}/{honorId}/{requirementId}") {
            ReportUploadScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        composable(Routes.MY_HONORS) {
            MyHonorsScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId ->
                    navController.navigate("${Routes.HONOR_DETAIL}/$honorId")
                }
            )
        }

        // TEEN: СОБЫТИЯ
        composable(Routes.EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId ->
                    navController.navigate("${Routes.EVENT_DETAIL}/$eventId")
                }
            )
        }

        composable("${Routes.EVENT_DETAIL}/{eventId}") {
            EventDetailScreen(onBack = { navController.popBackStack() })
        }

        // TEEN: ЗАДАЧИ
        composable(Routes.MY_TASKS) {
            MyTasksScreen(onBack = { navController.popBackStack() })
        }

        // TEEN: ПРОФИЛЬ
        composable(Routes.TEEN_PROFILE) {
            TeenProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun routeForRole(role: String): String = when (role) {
    "teen" -> Routes.TEEN_HOME
    "parent" -> Routes.PARENT_HOME
    "instructor" -> Routes.INSTRUCTOR_HOME
    "director" -> Routes.DIRECTOR_HOME
    "secretary" -> Routes.SECRETARY_HOME
    "conference" -> Routes.CONFERENCE_HOME
    else -> Routes.TEEN_HOME
}
''')

# ============================================================
# 3. LevelDao.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/local/dao/LevelDao.kt",
    '''
package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.local.relation.LevelWithSections
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelDao {

    @Query("SELECT * FROM levels ORDER BY `order`")
    fun observeLevels(): Flow<List<LevelEntity>>

    @Transaction
    @Query("SELECT * FROM levels ORDER BY `order`")
    fun observeLevelsWithSections(): Flow<List<LevelWithSections>>

    @Query("SELECT * FROM levels WHERE id = :id")
    suspend fun getLevel(id: String): LevelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLevels(levels: List<LevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLevel(level: LevelEntity)

    @Query("SELECT * FROM level_sections WHERE levelId = :levelId ORDER BY `order`")
    fun observeSections(levelId: String): Flow<List<LevelSectionEntity>>

    @Query("SELECT * FROM level_sections WHERE levelId = :levelId ORDER BY `order`")
    suspend fun getSections(levelId: String): List<LevelSectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSections(sections: List<LevelSectionEntity>)

    @Query("SELECT * FROM level_requirements WHERE levelId = :levelId ORDER BY `order`")
    fun observeRequirements(levelId: String): Flow<List<LevelRequirementEntity>>

    @Query("SELECT * FROM level_requirements WHERE levelId = :levelId ORDER BY `order`")
    suspend fun getRequirements(levelId: String): List<LevelRequirementEntity>

    @Query("SELECT * FROM level_requirements WHERE sectionId = :sectionId ORDER BY `order`")
    suspend fun getRequirementsBySection(sectionId: String): List<LevelRequirementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequirements(reqs: List<LevelRequirementEntity>)

    @Query("SELECT * FROM level_progress WHERE userId = :userId AND levelId = :levelId")
    fun observeProgress(userId: String, levelId: String): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress WHERE userId = :userId AND levelId = :levelId")
    suspend fun getProgressSnapshot(userId: String, levelId: String): List<LevelProgressEntity>

    @Query("SELECT * FROM level_progress WHERE userId = :userId AND requirementId = :reqId LIMIT 1")
    suspend fun getProgressForRequirement(userId: String, reqId: String): LevelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: LevelProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgressList(items: List<LevelProgressEntity>)

    @Update
    suspend fun updateProgress(p: LevelProgressEntity)

    @Query("UPDATE level_progress SET status = :status, approvedBy = :by, approvedAt = :at WHERE userId = :userId AND requirementId = :reqId")
    suspend fun approveRequirement(userId: String, reqId: String, status: String, by: String, at: Long)

    @Query("DELETE FROM level_progress WHERE userId = :userId AND requirementId = :requirementId")
    suspend fun clearProgressForRequirement(userId: String, requirementId: String)

    @Query("DELETE FROM level_progress WHERE userId = :userId AND levelId = :levelId")
    suspend fun clearProgress(userId: String, levelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(c: LevelCompletionEntity)

    @Query("SELECT * FROM level_completions WHERE userId = :userId")
    fun observeCompletions(userId: String): Flow<List<LevelCompletionEntity>>

    @Query("SELECT * FROM level_completions WHERE userId = :userId")
    suspend fun getCompletions(userId: String): List<LevelCompletionEntity>

    @Query("SELECT * FROM level_completions WHERE userId = :userId AND levelId = :levelId")
    suspend fun getCompletion(userId: String, levelId: String): LevelCompletionEntity?

    @Delete
    suspend fun deleteCompletion(c: LevelCompletionEntity)

    @Query("SELECT * FROM leader_checklists WHERE leaderId = :leaderId AND childId = :childId")
    fun observeChecklist(leaderId: String, childId: String): Flow<LeaderChecklistEntity?>

    @Query("SELECT * FROM leader_checklists WHERE leaderId = :leaderId AND childId = :childId AND levelId = :levelId LIMIT 1")
    suspend fun getChecklist(leaderId: String, childId: String, levelId: String): LeaderChecklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklist(c: LeaderChecklistEntity)

    @Query("SELECT * FROM leader_checklists WHERE childId = :childId")
    suspend fun getChecklistsForChild(childId: String): List<LeaderChecklistEntity>
}
''')

# ============================================================
# 4. LevelRepository.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/repository/LevelRepository.kt",
    '''
package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.LevelDao
import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.local.relation.LevelWithSections
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LevelRepository @Inject constructor(private val dao: LevelDao) {

    fun observeLevels(): Flow<List<LevelEntity>> = dao.observeLevels()

    fun observeLevelsWithSections(): Flow<List<LevelWithSections>> = dao.observeLevelsWithSections()

    suspend fun getLevel(id: String): LevelEntity? = dao.getLevel(id)

    fun observeSections(levelId: String): Flow<List<LevelSectionEntity>> = dao.observeSections(levelId)

    suspend fun getSections(levelId: String): List<LevelSectionEntity> = dao.getSections(levelId)

    fun observeRequirements(levelId: String): Flow<List<LevelRequirementEntity>> = dao.observeRequirements(levelId)

    suspend fun getRequirements(levelId: String): List<LevelRequirementEntity> = dao.getRequirements(levelId)

    suspend fun upsertLevels(levels: List<LevelEntity>) = dao.upsertLevels(levels)

    suspend fun upsertLevel(level: LevelEntity) = dao.upsertLevel(level)

    suspend fun upsertSections(sections: List<LevelSectionEntity>) = dao.upsertSections(sections)

    suspend fun upsertRequirements(reqs: List<LevelRequirementEntity>) = dao.upsertRequirements(reqs)

    fun observeProgress(userId: String, levelId: String): Flow<List<LevelProgressEntity>> = dao.observeProgress(userId, levelId)

    suspend fun getProgressSnapshot(userId: String, levelId: String): List<LevelProgressEntity> = dao.getProgressSnapshot(userId, levelId)

    suspend fun getRequirementsForSection(levelId: String, requirementId: String): List<LevelRequirementEntity> = dao.getRequirements(levelId).filter { it.id == requirementId }

    suspend fun upsertProgress(p: LevelProgressEntity) = dao.upsertProgress(p)

    suspend fun approveRequirement(userId: String, requirementId: String, status: String, approvedBy: String, at: Long) = dao.approveRequirement(userId, requirementId, status, approvedBy, at)

    suspend fun clearProgressForRequirement(userId: String, requirementId: String) = dao.clearProgressForRequirement(userId, requirementId)

    suspend fun upsertCompletion(c: LevelCompletionEntity) = dao.upsertCompletion(c)

    fun observeCompletions(userId: String): Flow<List<LevelCompletionEntity>> = dao.observeCompletions(userId)

    fun observeChecklist(leaderId: String, childId: String): Flow<LeaderChecklistEntity?> = dao.observeChecklist(leaderId, childId)

    suspend fun upsertChecklist(c: LeaderChecklistEntity) = dao.upsertChecklist(c)
}
''')

# ============================================================
# 5. HonorDao.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/local/dao/HonorDao.kt",
    '''
package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import com.pathfinder.hub.data.local.entity.learning.ContentVersionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCategoryEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorDraftEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.HonorVersionEntity
import com.pathfinder.hub.data.local.entity.learning.MemoryVerseEntity
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import com.pathfinder.hub.data.local.relation.HonorWithRequirements
import kotlinx.coroutines.flow.Flow

@Dao
interface HonorDao {

    @Query("SELECT * FROM honor_categories ORDER BY `order`")
    fun observeCategories(): Flow<List<HonorCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(c: HonorCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(cs: List<HonorCategoryEntity>)

    @Query("SELECT * FROM honors ORDER BY name")
    fun observeAllHonors(): Flow<List<HonorEntity>>

    @Query("SELECT * FROM honors WHERE categoryId = :categoryId ORDER BY name")
    fun observeHonorsByCategory(categoryId: String): Flow<List<HonorEntity>>

    @Query("SELECT * FROM honors WHERE id = :id")
    suspend fun getHonor(id: String): HonorEntity?

    @Transaction
    @Query("SELECT * FROM honors WHERE id = :id")
    fun observeHonorWithRequirements(id: String): Flow<HonorWithRequirements?>

    @Query("SELECT * FROM honors WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    fun searchHonors(query: String): Flow<List<HonorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHonors(hs: List<HonorEntity>)

    @Query("SELECT * FROM honor_requirements WHERE honorId = :honorId ORDER BY `order`")
    fun observeRequirements(honorId: String): Flow<List<HonorRequirementEntity>>

    @Query("SELECT * FROM honor_requirements WHERE honorId = :honorId ORDER BY `order`")
    suspend fun getRequirements(honorId: String): List<HonorRequirementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequirements(rs: List<HonorRequirementEntity>)

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND honorId = :honorId")
    fun observeProgress(userId: String, honorId: String): Flow<List<HonorProgressEntity>>

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND honorId = :honorId")
    suspend fun getProgressSnapshot(userId: String, honorId: String): List<HonorProgressEntity>

    @Query("SELECT * FROM honor_progress WHERE userId = :userId AND status = 'approved'")
    suspend fun getApprovedProgress(userId: String): List<HonorProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: HonorProgressEntity)

    @Query("UPDATE honor_progress SET status = :status, approvedBy = :by, approvedAt = :at WHERE userId = :userId AND requirementId = :reqId")
    suspend fun approveRequirement(userId: String, reqId: String, status: String, by: String, at: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(c: HonorCompletionEntity)

    @Query("SELECT * FROM honor_completions WHERE userId = :userId")
    fun observeCompletions(userId: String): Flow<List<HonorCompletionEntity>>

    @Query("SELECT * FROM honor_completions WHERE userId = :userId")
    suspend fun getCompletions(userId: String): List<HonorCompletionEntity>

    @Query("SELECT * FROM test_questions WHERE honorId = :honorId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestions(honorId: String, limit: Int): List<TestQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(qs: List<TestQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(a: TestAttemptEntity)

    @Query("SELECT MAX(attemptNumber) FROM test_attempts WHERE userId = :userId AND honorId = :honorId")
    suspend fun getLastAttemptNumber(userId: String, honorId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOpenAnswer(r: OpenAnswerReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(v: HonorVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(d: HonorDraftEntity)

    @Query("SELECT * FROM honor_drafts WHERE status = 'pending'")
    fun observePendingDrafts(): Flow<List<HonorDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(bs: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookReport(r: BookReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerses(vs: List<MemoryVerseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerseProgress(p: VerseProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContentVersion(v: ContentVersionEntity)

    @Query("SELECT * FROM content_versions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestContentVersion(): ContentVersionEntity?
}
''')

# ============================================================
# 6. HonorRepository.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/repository/HonorRepository.kt",
    '''
package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.HonorDao
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import com.pathfinder.hub.data.local.entity.learning.ContentVersionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCategoryEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorDraftEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.HonorVersionEntity
import com.pathfinder.hub.data.local.entity.learning.MemoryVerseEntity
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import com.pathfinder.hub.data.local.relation.HonorWithRequirements
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HonorRepository @Inject constructor(private val dao: HonorDao) {

    fun observeCategories(): Flow<List<HonorCategoryEntity>> = dao.observeCategories()
    suspend fun upsertCategory(c: HonorCategoryEntity) = dao.upsertCategory(c)
    suspend fun upsertCategories(cs: List<HonorCategoryEntity>) = dao.upsertCategories(cs)

    fun observeAllHonors(): Flow<List<HonorEntity>> = dao.observeAllHonors()
    fun observeHonorsByCategory(categoryId: String): Flow<List<HonorEntity>> = dao.observeHonorsByCategory(categoryId)
    suspend fun getHonor(id: String): HonorEntity? = dao.getHonor(id)
    fun observeHonorWithRequirements(id: String): Flow<HonorWithRequirements?> = dao.observeHonorWithRequirements(id)
    fun searchHonors(query: String): Flow<List<HonorEntity>> = dao.searchHonors(query)
    suspend fun upsertHonors(hs: List<HonorEntity>) = dao.upsertHonors(hs)

    fun observeRequirements(honorId: String): Flow<List<HonorRequirementEntity>> = dao.observeRequirements(honorId)
    suspend fun getRequirements(honorId: String): List<HonorRequirementEntity> = dao.getRequirements(honorId)
    suspend fun upsertRequirements(rs: List<HonorRequirementEntity>) = dao.upsertRequirements(rs)

    fun observeProgress(userId: String, honorId: String): Flow<List<HonorProgressEntity>> = dao.observeProgress(userId, honorId)
    suspend fun getProgressSnapshot(userId: String, honorId: String): List<HonorProgressEntity> = dao.getProgressSnapshot(userId, honorId)
    suspend fun upsertProgress(p: HonorProgressEntity) = dao.upsertProgress(p)
    suspend fun approveRequirement(userId: String, requirementId: String, status: String, approvedBy: String, at: Long) = dao.approveRequirement(userId, requirementId, status, approvedBy, at)
    suspend fun upsertCompletion(c: HonorCompletionEntity) = dao.upsertCompletion(c)
    fun observeCompletions(userId: String): Flow<List<HonorCompletionEntity>> = dao.observeCompletions(userId)

    suspend fun getRandomQuestions(honorId: String, limit: Int): List<TestQuestionEntity> = dao.getRandomQuestions(honorId, limit)
    suspend fun upsertQuestions(qs: List<TestQuestionEntity>) = dao.upsertQuestions(qs)
    suspend fun upsertAttempt(a: TestAttemptEntity) = dao.upsertAttempt(a)
    suspend fun getLastAttemptNumber(userId: String, honorId: String): Int? = dao.getLastAttemptNumber(userId, honorId)
    suspend fun upsertOpenAnswer(r: OpenAnswerReviewEntity) = dao.upsertOpenAnswer(r)

    suspend fun upsertVersion(v: HonorVersionEntity) = dao.upsertVersion(v)
    suspend fun upsertDraft(d: HonorDraftEntity) = dao.upsertDraft(d)
    fun observePendingDrafts(): Flow<List<HonorDraftEntity>> = dao.observePendingDrafts()

    suspend fun upsertBooks(bs: List<BookEntity>) = dao.upsertBooks(bs)
    suspend fun upsertBookReport(r: BookReportEntity) = dao.upsertBookReport(r)

    suspend fun upsertVerses(vs: List<MemoryVerseEntity>) = dao.upsertVerses(vs)
    suspend fun upsertVerseProgress(p: VerseProgressEntity) = dao.upsertVerseProgress(p)

    suspend fun upsertContentVersion(v: ContentVersionEntity) = dao.upsertContentVersion(v)
    suspend fun getLatestContentVersion(): ContentVersionEntity? = dao.getLatestContentVersion()
}
''')

# ============================================================
# ЗАПУСК
# ============================================================

if __name__ == "__main__":
    write_all()