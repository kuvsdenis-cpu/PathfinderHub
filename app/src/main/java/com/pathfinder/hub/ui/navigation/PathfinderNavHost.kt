package com.pathfinder.hub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pathfinder.hub.ui.challenges.ChallengeDetailScreen
import com.pathfinder.hub.ui.challenges.ChallengesScreen
import com.pathfinder.hub.ui.chat.ClubChatScreen
import com.pathfinder.hub.ui.chat.GroupChatScreen
import com.pathfinder.hub.ui.gamification.AchievementsScreen
import com.pathfinder.hub.ui.gamification.DigitalUniformScreen
import com.pathfinder.hub.ui.moderation.ModerationScreen
import com.pathfinder.hub.ui.notifications.NotificationsScreen
import com.pathfinder.hub.ui.reports.CreateReportScreen
import com.pathfinder.hub.ui.reports.ReportDetailScreen
import com.pathfinder.hub.ui.reports.ReportHistoryScreen
import com.pathfinder.hub.ui.screens.auth.LoginScreen
import com.pathfinder.hub.ui.screens.auth.RegisterScreen
import com.pathfinder.hub.ui.screens.events.CreateEventScreen
import com.pathfinder.hub.ui.screens.events.EventDetailScreen
import com.pathfinder.hub.ui.screens.events.EventsScreen
import com.pathfinder.hub.ui.screens.home.RoleHomeScreen
import com.pathfinder.hub.ui.screens.home.director.ApprovalsScreen
import com.pathfinder.hub.ui.screens.home.director.ClubMembersScreen
import com.pathfinder.hub.ui.screens.home.director.ClubSettingsScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorHomeScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorInvitesScreen
import com.pathfinder.hub.ui.screens.home.teen.TeenHomeScreen
import com.pathfinder.hub.ui.screens.honors.HonorCatalogScreen
import com.pathfinder.hub.ui.screens.honors.HonorDetailScreen
import com.pathfinder.hub.ui.screens.honors.HonorRequirementDetailScreen
import com.pathfinder.hub.ui.screens.honors.MyHonorsScreen
import com.pathfinder.hub.ui.screens.honors.ReportUploadScreen
import com.pathfinder.hub.ui.screens.honors.TestScreen
import com.pathfinder.hub.ui.screens.levels.LevelDetailScreen
import com.pathfinder.hub.ui.screens.levels.MyLevelsScreen
import com.pathfinder.hub.ui.screens.levels.RequirementDetailScreen
import com.pathfinder.hub.ui.screens.onboarding.OnboardingScreen
import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen
import com.pathfinder.hub.ui.screens.sync.SyncScreen
import com.pathfinder.hub.ui.screens.tasks.CreateTaskScreen
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
        // === Auth ===
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ROLE_HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToInvite = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                role = "teen",
                userId = "",
                onFinish = {
                    navController.navigate(Routes.ROLE_HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // === Role Router ===
        composable(Routes.ROLE_HOME) {
            RoleHomeScreen(navController = navController)
        }

        // === Director ===
        composable(Routes.DIRECTOR_HOME) {
            DirectorHomeScreen(
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToApprovals = { navController.navigate(Routes.APPROVALS) },
                onNavigateToReports = { navController.navigate(Routes.DIRECTOR_REPORTS) },
                onNavigateToEvents = { navController.navigate(Routes.CLUB_EVENTS) },
                onNavigateToSettings = { navController.navigate(Routes.CLUB_SETTINGS) },
                onNavigateToTasks = { navController.navigate(Routes.CREATE_TASK) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToModeration = { navController.navigate(Routes.MODERATION) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        composable(Routes.DIRECTOR_INVITES) {
            DirectorInvitesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CLUB_MEMBERS) {
            ClubMembersScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.APPROVALS) {
            ApprovalsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DIRECTOR_REPORTS) {
            ReportHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportClick = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                onCreateReport = { navController.navigate(Routes.CREATE_REPORT) }
            )
        }

        composable(Routes.CREATE_REPORT) {
            CreateReportScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.CLUB_EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
                onCreateEvent = { navController.navigate(Routes.CREATE_EVENT) }
            )
        }

        composable(Routes.CREATE_EVENT) {
            CreateEventScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.CLUB_SETTINGS) {
            ClubSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        // === Teen Home ===
        composable(Routes.TEEN_HOME) {
            TeenHomeScreen(
                onNavigateToLevels = { navController.navigate(Routes.MY_LEVELS) },
                onNavigateToHonors = { navController.navigate(Routes.HONOR_CATALOG) },
                onNavigateToMyHonors = { navController.navigate(Routes.MY_HONORS) },
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToTasks = { navController.navigate(Routes.MY_TASKS) },
                onNavigateToProfile = { navController.navigate(Routes.TEEN_PROFILE) },
                onNavigateToDigitalUniform = { navController.navigate(Routes.DIGITAL_UNIFORM) },
                onNavigateToAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                onNavigateToChallenges = { navController.navigate(Routes.CHALLENGES) },
                onNavigateToReports = { navController.navigate(Routes.REPORT_HISTORY) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        // === Teen - Levels ===
        composable(Routes.MY_LEVELS) {
            MyLevelsScreen(
                onBack = { navController.popBackStack() },
                onOpenLevel = { levelId -> navController.navigate(Routes.levelDetail(levelId)) }
            )
        }

        composable(
            route = Routes.LEVEL_DETAIL,
            arguments = listOf(navArgument("levelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val levelId = backStackEntry.arguments?.getString("levelId") ?: ""
            LevelDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { _, requirementId ->
                    navController.navigate(Routes.requirementDetail(requirementId))
                }
            )
        }

        composable(
            route = Routes.REQUIREMENT_DETAIL,
            arguments = listOf(navArgument("requirementId") { type = NavType.StringType })
        ) {
            RequirementDetailScreen(onBack = { navController.popBackStack() })
        }

        // === Teen - Honors ===
        composable(Routes.HONOR_CATALOG) {
            HonorCatalogScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId -> navController.navigate(Routes.honorDetail(honorId)) }
            )
        }

        composable(
            route = Routes.HONOR_DETAIL,
            arguments = listOf(navArgument("honorId") { type = NavType.StringType })
        ) {
            HonorDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { honorId, requirementId ->
                    navController.navigate(Routes.honorRequirementDetail(requirementId))
                }
            )
        }

        composable(
            route = Routes.HONOR_REQUIREMENT_DETAIL,
            arguments = listOf(navArgument("requirementId") { type = NavType.StringType })
        ) {
            HonorRequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenTest = { navController.navigate(Routes.testScreen("placeholder_honor_id")) },
                onOpenReport = { navController.navigate(Routes.reportUpload("placeholder_req_id")) }
            )
        }

        composable(Routes.MY_HONORS) {
            MyHonorsScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId -> navController.navigate(Routes.honorDetail(honorId)) }
            )
        }

        composable(
            route = Routes.TEST_SCREEN,
            arguments = listOf(navArgument("honorId") { type = NavType.StringType })
        ) {
            TestScreen(
                onBack = { navController.popBackStack() },
                onFinished = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.REPORT_UPLOAD,
            arguments = listOf(navArgument("requirementId") { type = NavType.StringType })
        ) {
            ReportUploadScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        // === Teen - Events & Tasks ===
        composable(Routes.EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) }
                // onCreateEvent не передан (равен null), поэтому кнопка "+" у следопыта скрыта
            )
        }

        composable(
            route = Routes.EVENT_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) {
            EventDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MY_TASKS) {
            MyTasksScreen(
                onBack = { navController.popBackStack() },
                onCreateTask = { navController.navigate(Routes.CREATE_TASK) } // ✅ ИСПРАВЛЕНО: следопыт может создать задачу
            )
        }

        // === Teen - Profile ===
        composable(Routes.TEEN_PROFILE) {
            TeenProfileScreen(onBack = { navController.popBackStack() })
        }

        // === Teen - Gamification ===
        composable(Routes.DIGITAL_UNIFORM) {
            DigitalUniformScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.ACHIEVEMENTS) {
            AchievementsScreen(onNavigateBack = { navController.popBackStack() })
        }

        // === Teen - Challenges ===
        composable(Routes.CHALLENGES) {
            ChallengesScreen(
                onNavigateBack = { navController.popBackStack() },
                onChallengeClick = { challengeId -> navController.navigate(Routes.challengeDetail(challengeId)) }
            )
        }

        composable(
            route = Routes.CHALLENGE_DETAIL,
            arguments = listOf(navArgument("challengeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val challengeId = backStackEntry.arguments?.getString("challengeId") ?: ""
            ChallengeDetailScreen(
                challengeId = challengeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // === Reports ===
        composable(Routes.REPORT_HISTORY) {
            ReportHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportClick = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                onCreateReport = { navController.navigate(Routes.CREATE_REPORT) } // ✅ ИСПРАВЛЕНО: кнопка "+" теперь работает
            )
        }

        composable(
            route = Routes.REPORT_DETAIL,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            ReportDetailScreen(
                reportId = reportId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // === Чаты и Модерация ===
        composable(Routes.CLUB_CHAT) {
            ClubChatScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.GROUP_CHAT,
            arguments = listOf(
                navArgument("targetType") { type = NavType.StringType },
                navArgument("targetId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val targetType = backStackEntry.arguments?.getString("targetType") ?: ""
            val targetId = backStackEntry.arguments?.getString("targetId") ?: ""
            GroupChatScreen(
                targetType = targetType,
                targetId = targetId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MODERATION) {
            ModerationScreen(onBack = { navController.popBackStack() })
        }

        // === Notifications ===
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        // === Sync ===
        composable(Routes.SYNC) {
            SyncScreen(onBack = { navController.popBackStack() })
        }
    }
}