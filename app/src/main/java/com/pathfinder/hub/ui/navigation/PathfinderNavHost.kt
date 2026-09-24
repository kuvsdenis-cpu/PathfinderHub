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
