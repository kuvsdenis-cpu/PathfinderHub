package com.pathfinder.hub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pathfinder.hub.MainActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.info.InfoHubScreen
import com.pathfinder.hub.ui.info.InfoCategoryScreen
import com.pathfinder.hub.ui.info.InfoArticleScreen
import com.pathfinder.hub.ui.info.InfoEditorScreen
import com.pathfinder.hub.ui.challenges.ChallengeDetailScreen
import com.pathfinder.hub.ui.challenges.ChallengesScreen
import com.pathfinder.hub.ui.chat.list.ChatsListScreen
import com.pathfinder.hub.ui.chat.dm.DMChatScreen
import com.pathfinder.hub.ui.chat.group.CreateGroupChatScreen
import com.pathfinder.hub.ui.chat.group.CustomGroupChatScreen
import com.pathfinder.hub.ui.chat.ClubChatScreen
import com.pathfinder.hub.ui.chat.GroupChatScreen
import com.pathfinder.hub.ui.gamification.AchievementsScreen
import com.pathfinder.hub.ui.gamification.DigitalUniformScreen
import com.pathfinder.hub.ui.moderation.ModerationScreen
import com.pathfinder.hub.ui.notifications.NotificationsScreen
import com.pathfinder.hub.ui.activity.ActivityScreen
import com.pathfinder.hub.ui.qr.QrScannerScreen
import com.pathfinder.hub.ui.qr.ManageStationsScreen
import com.pathfinder.hub.ui.qr.CreateStationScreen
import com.pathfinder.hub.ui.qr.quests.ManageQuestsScreen
import com.pathfinder.hub.ui.qr.quests.QuestDetailScreen
import com.pathfinder.hub.ui.qr.quests.QuestEditorScreen
import com.pathfinder.hub.ui.navigation.Routes
import com.pathfinder.hub.ui.reports.CreateReportScreen
import com.pathfinder.hub.ui.reports.ReportDetailScreen
import com.pathfinder.hub.ui.reports.ReportHistoryScreen
import com.pathfinder.hub.ui.screens.auth.LoginScreen
import com.pathfinder.hub.ui.screens.auth.RegisterScreen
import com.pathfinder.hub.ui.screens.events.CreateEventScreen
import com.pathfinder.hub.ui.screens.events.EventDetailScreen
import com.pathfinder.hub.ui.screens.events.EventConsentsScreen
import com.pathfinder.hub.ui.screens.crossclub.CrossClubScreen
import com.pathfinder.hub.ui.screens.crossclub.CrossClubTeenScreen
import com.pathfinder.hub.ui.screens.home.conference.events.ConferenceEventsScreen
import com.pathfinder.hub.ui.screens.home.conference.analytics.ConferenceAnalyticsScreen
import com.pathfinder.hub.ui.screens.home.conference.events.CreateConferenceEventScreen
import com.pathfinder.hub.ui.screens.events.EventsScreen
import com.pathfinder.hub.ui.screens.home.RoleHomeScreen
import com.pathfinder.hub.ui.screens.home.director.ApprovalsScreen
import com.pathfinder.hub.ui.screens.home.director.ClubMembersScreen
import com.pathfinder.hub.ui.screens.home.director.ClubSettingsScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorHomeScreen
import com.pathfinder.hub.ui.screens.home.director.DirectorInvitesScreen
import com.pathfinder.hub.ui.screens.home.director.staff.StaffScreen
import com.pathfinder.hub.ui.screens.home.director.sections.DirectorPeopleScreen
import com.pathfinder.hub.ui.screens.home.director.sections.DirectorLearningScreen
import com.pathfinder.hub.ui.screens.home.director.sections.DirectorPlanningScreen
import com.pathfinder.hub.ui.screens.home.director.sections.DirectorCommunicationsScreen
import com.pathfinder.hub.ui.screens.home.director.sections.DirectorFinanceScreen
import com.pathfinder.hub.ui.screens.home.director.sections.DirectorManagementScreen
import com.pathfinder.hub.ui.screens.home.director.staff.ManagePermissionsScreen
import com.pathfinder.hub.ui.screens.home.director.scout.ScoutDetailScreen
import com.pathfinder.hub.ui.screens.home.teen.TeenHomeScreen
import com.pathfinder.hub.ui.screens.home.instructor.InstructorHomeScreen
import com.pathfinder.hub.ui.screens.home.secretary.SecretaryHomeScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentHomeScreen
import com.pathfinder.hub.ui.screens.home.parent.ChildProgressScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentPaymentsScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentMessagesScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentMeetingsScreen
import com.pathfinder.hub.ui.screens.home.parent.ParentConsentsScreen
import com.pathfinder.hub.ui.screens.parent_meetings.CreateParentMeetingScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceHomeScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceSecretaryHomeScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceClubsScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceMembersScreen
import com.pathfinder.hub.ui.screens.home.conference.ConferenceInvitesScreen
import com.pathfinder.hub.ui.screens.homework.CreateHomeworkScreen
import com.pathfinder.hub.ui.screens.homework.review.ReviewHomeworkScreen
import com.pathfinder.hub.ui.screens.archive.ArchiveScreen
import com.pathfinder.hub.ui.screens.groups.ManageCustomGroupsScreen
import com.pathfinder.hub.ui.screens.groups.CreateCustomGroupScreen
import com.pathfinder.hub.ui.screens.club_info.ClubInfoScreen
import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen
import com.pathfinder.hub.ui.screens.home.director.deleted.DeletedClubsScreen
import com.pathfinder.hub.ui.screens.payments.DirectorPaymentsScreen
import com.pathfinder.hub.ui.screens.payments.CreatePaymentScreen
import com.pathfinder.hub.ui.screens.certificates.MyCertificatesScreen
import com.pathfinder.hub.ui.screens.honors.HonorCatalogScreen
import com.pathfinder.hub.ui.screens.books.BooksScreen
import com.pathfinder.hub.ui.screens.books.BookDetailScreen
import com.pathfinder.hub.ui.screens.books.BookReaderScreen
import com.pathfinder.hub.ui.screens.books.CreateBookScreen
import com.pathfinder.hub.ui.screens.honors.HonorDetailScreen
import com.pathfinder.hub.ui.screens.honors.HonorRequirementDetailScreen
import com.pathfinder.hub.ui.screens.honors.MyHonorsScreen
import com.pathfinder.hub.ui.screens.honors.TestScreen
import com.pathfinder.hub.ui.screens.levels.LevelDetailScreen
import com.pathfinder.hub.ui.screens.levels.MyLevelsScreen
import com.pathfinder.hub.ui.screens.levels.RequirementDetailScreen
import com.pathfinder.hub.ui.learning.content.ContentManagerScreen
import com.pathfinder.hub.ui.learning.content.ContentLevelScreen
import com.pathfinder.hub.ui.learning.content.ContentHonorScreen
import com.pathfinder.hub.ui.learning.content.ContentSectionScreen
import com.pathfinder.hub.ui.learning.editor.RequirementEditorScreen
import com.pathfinder.hub.ui.screens.onboarding.OnboardingScreen
import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen
import com.pathfinder.hub.ui.screens.settings.SettingsScreen
import com.pathfinder.hub.ui.screens.sync.SyncScreen
import com.pathfinder.hub.ui.screens.tasks.CreateTaskScreen
import com.pathfinder.hub.ui.screens.tasks.MyTasksScreen
import com.pathfinder.hub.ui.screens.tests.openanswers.OpenAnswersScreen
import com.pathfinder.hub.ui.screens.tests.openanswers.OpenAnswerReviewScreen
import com.pathfinder.hub.ui.storage.FileUploadResult
import com.pathfinder.hub.ui.bible.BibleBooksScreen
import com.pathfinder.hub.ui.bible.BibleBookScreen
import com.pathfinder.hub.ui.bible.BibleChapterScreen
import com.pathfinder.hub.ui.bible.BibleBookmarksScreen
import com.pathfinder.hub.ui.bible.BibleSearchScreen
import com.pathfinder.hub.ui.pearls.PearlsScreen
import com.pathfinder.hub.ui.pearls.PearlDetailScreen
import com.pathfinder.hub.ui.pearls.PearlTrainingScreen
import com.pathfinder.hub.ui.pearls.PearlAdminScreen
import com.pathfinder.hub.ui.readingplan.ReadingPlansScreen
import com.pathfinder.hub.ui.readingplan.ReadingPlanDetailScreen
import com.pathfinder.hub.ui.readingplan.ReadingPlanDayScreen
import com.pathfinder.hub.ui.readingplan.ReadingPlanAdminScreen
import com.pathfinder.hub.ui.storage.FileUploadScreen
import com.pathfinder.hub.ui.learning.content.TestEditorScreen
import com.pathfinder.hub.ui.learning.content.TestQuestionEditorScreen

// === Импорты для Субботней школы ===
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pathfinder.hub.ui.sabbath.SabbathLessonScreen
import com.pathfinder.hub.ui.sabbath.SabbathSchoolListScreen
import com.pathfinder.hub.ui.sabbath.SabbathSchoolListViewModel
import com.pathfinder.hub.ui.sabbath.SabbathSchoolSettingsScreen
import com.pathfinder.hub.ui.sabbath.SabbathSchoolViewModel


@Composable
fun PathfinderNavHost(
    navController: NavHostController,
    startDestination: String = Routes.LOGIN
) {
    LaunchedEffect(Unit) {
        MainActivity.deepLinks.collect { link ->
            when (link.type) {
                "dm" -> {
                    val other = link.otherUid ?: ""
                    navController.navigate(Routes.dmChat(link.targetId, other))
                }
                "group" -> navController.navigate(Routes.customGroupChat(link.targetId))
                "club_chat" -> navController.navigate(Routes.CLUB_CHAT)
                "chats_list" -> navController.navigate(Routes.CHATS_LIST)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ===== Auth =====
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ROLE_HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToInvite = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { role, userId ->
                    navController.navigate(Routes.onboarding(role, userId)) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.ONBOARDING,
            arguments = listOf(
                navArgument("role") { type = NavType.StringType; defaultValue = "teen" },
                navArgument("userId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "teen"
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            OnboardingScreen(
                role = role,
                userId = userId,
                onFinish = {
                    navController.navigate(Routes.ROLE_HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ROLE_HOME) {
            RoleHomeScreen(navController = navController)
        }

        // ===== Instructor =====
        composable(Routes.INSTRUCTOR_HOME) {
            InstructorHomeScreen(
                onNavigateToApprovals = { navController.navigate(Routes.APPROVALS) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToModeration = { navController.navigate(Routes.MODERATION) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToQrStations = { navController.navigate(Routes.MY_STATIONS) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToOpenAnswers = { navController.navigate(Routes.OPEN_ANSWERS) },
                onNavigateToContentManager = { navController.navigate(Routes.CONTENT_MANAGER) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onNavigateToSabbathSchool = { navController.navigate(Routes.SABBATH_SCHOOL_LIST) }, // <-- ДОБАВЛЕНО
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }

        // ===== Secretary =====
        composable(Routes.SECRETARY_HOME) {
            SecretaryHomeScreen(
                onNavigateToInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
                onNavigateToMembers = { navController.navigate(Routes.CLUB_MEMBERS) },
                onNavigateToReports = { navController.navigate(Routes.DIRECTOR_REPORTS) },
                onNavigateToEvents = { navController.navigate(Routes.CLUB_EVENTS) },
                onNavigateToTasks = { navController.navigate(Routes.CREATE_TASK) },
                onNavigateToClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToSabbathSchool = { navController.navigate(Routes.SABBATH_SCHOOL_LIST) }, // <-- ДОБАВЛЕНО
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }

        // ===== Parent =====
        composable(Routes.PARENT_HOME) {
            ParentHomeScreen(
                onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenChild = { childId -> navController.navigate(Routes.childProgress(childId)) },
                onNavigateToPayments = { navController.navigate(Routes.PARENT_PAYMENTS) },
                onNavigateToMessages = { navController.navigate(Routes.PARENT_MESSAGES) },
                onNavigateToMeetings = { navController.navigate(Routes.PARENT_MEETINGS) },
                onNavigateToConsents = { navController.navigate(Routes.PARENT_CONSENTS) },
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(
            route = Routes.CHILD_PROGRESS,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            ChildProgressScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PARENT_PAYMENTS) {
            ParentPaymentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PARENT_MESSAGES) {
            ParentMessagesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PARENT_MEETINGS) {
            ParentMeetingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE_PARENT_MEETING) {
            CreateParentMeetingScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.PARENT_CONSENTS) {
            ParentConsentsScreen(onBack = { navController.popBackStack() })
        }

        // ===== Conference =====
        composable(Routes.CONFERENCE_HOME) {
            ConferenceHomeScreen(
                onNavigateToClubs = { navController.navigate(Routes.CONFERENCE_CLUBS) },
                onNavigateToMembers = { navController.navigate(Routes.CONFERENCE_MEMBERS) },
                onNavigateToInvites = { navController.navigate(Routes.CONFERENCE_INVITES) },
                onNavigateToEvents = { navController.navigate(Routes.CONFERENCE_EVENTS) },
                onNavigateToAnalytics = { navController.navigate(Routes.CONFERENCE_ANALYTICS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.CONFERENCE_SECRETARY_HOME) {
            ConferenceSecretaryHomeScreen(
                onNavigateToClubs = { navController.navigate(Routes.CONFERENCE_CLUBS) },
                onNavigateToMembers = { navController.navigate(Routes.CONFERENCE_MEMBERS) },
                onNavigateToEvents = { navController.navigate(Routes.CONFERENCE_EVENTS) },
                onNavigateToAnalytics = { navController.navigate(Routes.CONFERENCE_ANALYTICS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.CONFERENCE_CLUBS) {
            ConferenceClubsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONFERENCE_MEMBERS) {
            ConferenceMembersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONFERENCE_INVITES) {
            ConferenceInvitesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CONFERENCE_EVENTS) {
            ConferenceEventsScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(Routes.CREATE_CONFERENCE_EVENT) }
            )
        }

        composable(Routes.CREATE_CONFERENCE_EVENT) {
            CreateConferenceEventScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.CONFERENCE_ANALYTICS) {
            ConferenceAnalyticsScreen(onBack = { navController.popBackStack() })
        }

        // ===== Director =====
        composable(Routes.DIRECTOR_HOME) {
            DirectorHomeScreen(
                onNavigateToPeople = { navController.navigate(Routes.DIRECTOR_PEOPLE) },
                onNavigateToLearning = { navController.navigate(Routes.DIRECTOR_LEARNING) },
                onNavigateToPlanning = { navController.navigate(Routes.DIRECTOR_PLANNING) },
                onNavigateToCommunications = { navController.navigate(Routes.DIRECTOR_COMMUNICATIONS) },
                onNavigateToFinance = { navController.navigate(Routes.DIRECTOR_FINANCE) },
                onNavigateToManagement = { navController.navigate(Routes.DIRECTOR_MANAGEMENT) },
                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToDeletedClubs = { navController.navigate(Routes.DELETED_CLUBS) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToBible = { navController.navigate(Routes.BIBLE) },
                onNavigateToPearls = { navController.navigate(Routes.PEARLS) },
                onNavigateToPearlsAdmin = { navController.navigate(Routes.PEARLS_ADMIN) },
                onNavigateToReadingPlan = { navController.navigate(Routes.READING_PLANS) },
                onNavigateToReadingPlanAdmin = { navController.navigate(Routes.READING_PLANS_ADMIN) },
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onNavigateToSabbathSchool = { navController.navigate(Routes.SABBATH_SCHOOL_LIST) }, // <-- ДОБАВЛЕНО
                onNavigateToSabbathSchoolSettings = { navController.navigate(Routes.SABBATH_SCHOOL_SETTINGS) },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.DIRECTOR_PEOPLE) {
            DirectorPeopleScreen(
                onBack = { navController.popBackStack() },
                onOpenScout = { scoutId -> navController.navigate(Routes.scoutDetail(scoutId)) },
                onOpenPermissions = { userId ->
                    navController.navigate(Routes.managePermissions(userId))
                },
                onOpenDM = { dmId, otherUid ->
                    navController.navigate(Routes.dmChat(dmId, otherUid))
                },
                onOpenMember = { userId ->
                    navController.navigate(Routes.childProgress(userId))
                },
                onOpenGroup = { groupId ->
                    navController.navigate(Routes.customGroupEditor(groupId))
                },
                onCreateGroup = {
                    navController.navigate(Routes.customGroupEditor("new"))
                },
            )
        }
        composable(Routes.DIRECTOR_LEARNING) {
            DirectorLearningScreen(
                onBack = { navController.popBackStack() },
                onOpenLibrary = { navController.navigate(Routes.BOOKS) },
                onOpenAttempt = { attemptId ->
                    navController.navigate(Routes.openAnswerReview(attemptId))
                },
                onOpenLevel = { levelId ->
                    navController.navigate(Routes.contentLevel(levelId))
                },
                onOpenHonor = { honorId ->
                    navController.navigate(Routes.contentHonor(honorId))
                },
                onOpenTest = { testId ->
                    navController.navigate(Routes.testEditor(testId))
                },
                onCreateHomework = {
                    navController.navigate(Routes.CREATE_HOMEWORK)
                },
                onCreateTask = {
                    navController.navigate(Routes.CREATE_TASK)
                },
                onCreateTest = {
                    navController.navigate(Routes.testEditor("new"))
                },
            )
        }
        composable(Routes.DIRECTOR_PLANNING) {
            DirectorPlanningScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId ->
                    navController.navigate(Routes.eventDetail(eventId))
                },
                onCreateEvent = {
                    navController.navigate(Routes.CREATE_EVENT)
                },
                onCreateParentMeeting = {
                    navController.popBackStack()
                },
                onCreateStation = {
                    navController.navigate(Routes.createStation(null))
                },
                onOpenQuestReadonly = { questId ->
                    navController.navigate(Routes.questView(questId))
                },
                onOpenQuest = { questId ->
                    navController.navigate(Routes.questEditor(questId))
                },
                onCreateQuest = {
                    navController.navigate(Routes.questEditor("new"))
                },
            )
        }
        composable(Routes.DIRECTOR_COMMUNICATIONS) { backStackEntry ->
            DirectorCommunicationsScreen(
                onBack = { navController.popBackStack() },
                onOpenDM = { dmId, otherUid ->
                    navController.navigate(Routes.dmChat(dmId, otherUid))
                },
                onOpenGroup = { groupId ->
                    navController.navigate(Routes.customGroupChat(groupId))
                },
                onCreateGroup = {
                    navController.navigate(Routes.CREATE_GROUP_CHAT)
                },
                onAttachFile = { clubId ->
                    navController.navigate(Routes.fileUpload("CHAT", clubId))
                },
                savedStateHandle = backStackEntry.savedStateHandle,
            )
        }
        composable(Routes.DIRECTOR_FINANCE) {
            DirectorFinanceScreen(
                onBack = { navController.popBackStack() },
                onCreatePayment = {
                    navController.navigate(Routes.CREATE_PAYMENT)
                },
            )
        }
        composable(Routes.DIRECTOR_MANAGEMENT) {
            DirectorManagementScreen(
                onBack = { navController.popBackStack() },
                onOpenClubInfo = { clubId ->
                    navController.navigate(Routes.clubInfo(clubId))
                },
                onCreateReport = {
                    navController.navigate(Routes.CREATE_REPORT)
                },
                onOpenReport = { reportId ->
                    navController.navigate(Routes.reportDetail(reportId))
                },
                onNavigateToSync = { navController.navigate(Routes.SYNC) },
            )
        }
        composable(Routes.DELETED_CLUBS) {
            DeletedClubsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DIRECTOR_INVITES) { DirectorInvitesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CLUB_MEMBERS) {
            ClubMembersScreen(
                onBack = { navController.popBackStack() },
                onOpenDM = { dmId, otherUid -> navController.navigate(Routes.dmChat(dmId, otherUid)) },
                onOpenMember = { userId -> navController.navigate(Routes.childProgress(userId)) },
            )
        }
        composable(Routes.APPROVALS) { ApprovalsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DIRECTOR_REPORTS) {
            ReportHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportClick = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                onCreateReport = { navController.navigate(Routes.CREATE_REPORT) }
            )
        }
        composable(Routes.CREATE_REPORT) {
            CreateReportScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.CLUB_EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
                onCreateEvent = { navController.navigate(Routes.CREATE_EVENT) }
            )
        }
        composable(Routes.CREATE_EVENT) {
            CreateEventScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.CLUB_SETTINGS) { ClubSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.MY_STATIONS) {
            ManageStationsScreen(
                onBack = { navController.popBackStack() },
                onCreateStation = { navController.navigate(Routes.createStation(null)) },
                onOpenQuestReadonly = { questId ->
                    navController.navigate(Routes.questView(questId))
                },
            )
        }
        composable(
            route = Routes.CREATE_STATION,
            arguments = listOf(
                navArgument("questId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            CreateStationScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }
        composable(Routes.STAFF) {
            StaffScreen(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { userId -> navController.navigate(Routes.managePermissions(userId)) },
                onOpenInvites = { navController.navigate(Routes.DIRECTOR_INVITES) },
            )
        }
        composable(
            route = Routes.MANAGE_PERMISSIONS,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ManagePermissionsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.SCOUT_DETAIL,
            arguments = listOf(navArgument("scoutId") { type = NavType.StringType })
        ) {
            ScoutDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QUEST_MANAGE) {
            ManageQuestsScreen(
                onBack = { navController.popBackStack() },
                onOpenQuest = { questId -> navController.navigate(Routes.questEditor(questId)) },
                onCreateQuest = { navController.navigate(Routes.questEditor("new")) }
            )
        }
        composable(
            route = Routes.QUEST_EDITOR,
            arguments = listOf(navArgument("questId") { type = NavType.StringType })
        ) {
            QuestEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onCreateStation = { qid ->
                    navController.navigate(Routes.createStation(qid))
                },
            )
        }
        composable(
            route = Routes.QUEST_VIEW,
            arguments = listOf(navArgument("questId") { type = NavType.StringType })
        ) {
            QuestEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                readOnly = true,
            )
        }
        composable(Routes.CREATE_HOMEWORK) {
            CreateHomeworkScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.REVIEW_HOMEWORK) { ReviewHomeworkScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.CREATE_CLUB) {
            CreateClubScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }
        composable(Routes.DIRECTOR_PAYMENTS) {
            DirectorPaymentsScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(Routes.CREATE_PAYMENT) }
            )
        }
        composable(Routes.CREATE_PAYMENT) {
            CreatePaymentScreen(onBack = { navController.popBackStack() }, onCreated = { navController.popBackStack() })
        }

        composable(Routes.CROSS_CLUB) {
            CrossClubScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CROSS_CLUB_TEEN) {
            CrossClubTeenScreen(onBack = { navController.popBackStack() })
        }

        // ===== Reading Plan (B4.1) =====
        composable(Routes.READING_PLANS) {
            ReadingPlansScreen(
                onBack = { navController.popBackStack() },
                onOpenPlan = { id -> navController.navigate(Routes.readingPlanDetail(id)) },
            )
        }
        composable(
            route = Routes.READING_PLAN_DETAIL,
            arguments = listOf(navArgument("scheduleId") { type = NavType.StringType })
        ) {
            ReadingPlanDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenDay = { id -> navController.navigate(Routes.readingPlanDay(id)) },
            )
        }
        composable(
            route = Routes.READING_PLAN_DAY,
            arguments = listOf(navArgument("dayId") { type = NavType.StringType })
        ) {
            ReadingPlanDayScreen(
                onBack = { navController.popBackStack() },
                onOpenInBible = { bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(bookId, chapter))
                },
            )
        }
        composable(Routes.READING_PLANS_ADMIN) {
            ReadingPlanAdminScreen(onBack = { navController.popBackStack() })
        }

        // ===== Pearls Admin (B3.2) =====
        composable(Routes.PEARLS_ADMIN) {
            PearlAdminScreen(onBack = { navController.popBackStack() })
        }

        // ===== Pearls (B3.1) =====
        composable(Routes.PEARLS) {
            PearlsScreen(
                onBack = { navController.popBackStack() },
                onOpenPearl = { pearlId ->
                    navController.navigate(Routes.pearlDetail(pearlId))
                },
            )
        }
        composable(
            route = Routes.PEARL_DETAIL,
            arguments = listOf(navArgument("pearlId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pearlId = backStackEntry.arguments?.getString("pearlId") ?: ""
            PearlDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenInBible = { bookId, chapter, verseId ->
                    navController.navigate(Routes.bibleChapter(bookId, chapter, verseId))
                },
                onTrain = { navController.navigate(Routes.pearlTraining(pearlId)) },
            )
        }
        composable(
            route = Routes.PEARL_TRAINING,
            arguments = listOf(navArgument("pearlId") { type = NavType.StringType })
        ) {
            PearlTrainingScreen(onBack = { navController.popBackStack() })
        }

        // ===== Bible (B2) =====
        composable(Routes.BIBLE) {
            BibleBooksScreen(
                onBack = { navController.popBackStack() },
                onOpenBookmarks = { navController.navigate(Routes.BIBLE_BOOKMARKS) },
                onOpenBook = { bookId -> navController.navigate(Routes.bibleBook(bookId)) },
                onOpenSearch = { navController.navigate(Routes.BIBLE_SEARCH) },
            )
        }
        composable(Routes.BIBLE_BOOKMARKS) {
            BibleBookmarksScreen(
                onBack = { navController.popBackStack() },
                onOpenVerse = { bookId, chapter, verseId ->
                    navController.navigate(Routes.bibleChapter(bookId, chapter, verseId))
                },
                onOpenBible = { navController.navigate(Routes.BIBLE) },
            )
        }
        composable(
            route = Routes.BIBLE_BOOK,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            BibleBookScreen(
                onBack = { navController.popBackStack() },
                onOpenChapter = { bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(bookId, chapter))
                },
            )
        }
        composable(Routes.BIBLE_SEARCH) {
            BibleSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenVerse = { bookId, chapter, verseId ->
                    navController.navigate(Routes.bibleChapter(bookId, chapter, verseId))
                },
            )
        }
        composable(
            route = Routes.BIBLE_CHAPTER,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapter") { type = NavType.IntType },
                navArgument("verseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            )
        ) {
            BibleChapterScreen(
                onBack = { navController.popBackStack() },
                onOpenBookmarks = { navController.navigate(Routes.BIBLE_BOOKMARKS) },
                onOpenChapter = { bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(bookId, chapter))
                },
            )
        }

        // ===== Books =====
        composable(Routes.BOOKS) {
            BooksScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(Routes.bookDetail(id)) },
                onCreateBook = { navController.navigate(Routes.CREATE_BOOK) },
            )
        }
        composable(Routes.CREATE_BOOK) {
            CreateBookScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            BookDetailScreen(
                onBack = { navController.popBackStack() },
                onRead = { navController.navigate(Routes.bookReader(bookId)) },
                onCreateTest = {
                    navController.navigate(Routes.testEditorForScope("book", bookId))
                },
                onOpenTest = { testId ->
                    navController.navigate(Routes.test(testId))
                },
            )
        }
        composable(
            route = Routes.BOOK_READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) {
            BookReaderScreen(
                onBack = { navController.popBackStack() },
                onDeleteBook = { navController.popBackStack() },
            )
        }

        // ===== Teen Home =====
        composable(Routes.TEEN_HOME) {
            TeenHomeScreen(
                onNavigateToClubInfo = { clubId ->
                    navController.navigate(Routes.clubInfo(clubId))
                },
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
                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToCertificates = { navController.navigate(Routes.MY_CERTIFICATES) },
                onNavigateToQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onNavigateToActivity = { navController.navigate(Routes.ACTIVITY) },
                onNavigateToCrossClub = { navController.navigate(Routes.CROSS_CLUB_TEEN) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToBible = { navController.navigate(Routes.BIBLE) },
                onNavigateToPearls = { navController.navigate(Routes.PEARLS) },
                onNavigateToReadingPlan = { navController.navigate(Routes.READING_PLANS) },
                onNavigateToBooks = { navController.navigate(Routes.BOOKS) },
                onNavigateToInfo = { navController.navigate(Routes.INFO_HUB) },
                onNavigateToSabbathSchool = { navController.navigate(Routes.SABBATH_SCHOOL_LIST) } // <-- ДОБАВЛЕНО
            )
        }
        composable(Routes.MY_CERTIFICATES) { MyCertificatesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.QR_SCANNER) {
            QrScannerScreen(
                onBack = { navController.popBackStack() },
                onOpenQuest = { questId ->
                    navController.navigate(Routes.questDetail(questId))
                },
            )
        }
        composable(
            route = Routes.QUEST_DETAIL,
            arguments = listOf(navArgument("questId") { type = NavType.StringType })
        ) {
            QuestDetailScreen(
                onBack = { navController.popBackStack() },
                onScan = { navController.navigate(Routes.QR_SCANNER) },
                onOpenTest = { testId ->
                    navController.navigate(Routes.test(testId))
                },
            )
        }
        composable(Routes.ACTIVITY) { ActivityScreen(onBack = { navController.popBackStack() }) }

        // ===== Teen Levels =====
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
            LevelDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementDetail(lvlId, requirementId))
                },
                onUploadReport = { lvlId, requirementId ->
                    navController.navigate(Routes.fileUpload("LEVEL_REQUIREMENT", requirementId))
                },
                onOpenTest = { testId ->
                    navController.navigate(Routes.test(testId))
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(
            route = Routes.REQUIREMENT_DETAIL,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            RequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onUploadReport = { lvlId, requirementId ->
                    navController.navigate(Routes.fileUpload("LEVEL_REQUIREMENT", requirementId))
                },
                onOpenEditor = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementEditor(lvlId, requirementId))
                },
                onAttachTaskFile = { requirementId, taskId ->
                    navController.navigate(Routes.fileUpload("TASK_ANSWER", "$requirementId|$taskId"))
                },
                onOpenBook = { bookId ->
                    navController.navigate(Routes.bookDetail(bookId))
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(
            route = Routes.REQUIREMENT_EDITOR,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) {
            RequirementEditorScreen(onBack = { navController.popBackStack() })
        }

        // ===== Content Manager =====
        composable(Routes.CONTENT_MANAGER) {
            ContentManagerScreen(
                onBack = { navController.popBackStack() },
                onOpenLevel = { levelId -> navController.navigate(Routes.contentLevel(levelId)) },
                onOpenHonor = { honorId -> navController.navigate(Routes.contentHonor(honorId)) },
                onOpenTest = { testId -> navController.navigate(Routes.testEditor(testId)) }
            )
        }
        composable(
            route = Routes.CONTENT_HONOR,
            arguments = listOf(navArgument("honorId") { type = NavType.StringType })
        ) {
            ContentHonorScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { honorId, requirementId ->
                    navController.navigate(Routes.requirementEditor(honorId, requirementId))
                },
            )
        }
        composable(
            route = Routes.TEST_EDITOR,
            arguments = listOf(navArgument("testId") { type = NavType.StringType })
        ) {
            TestEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onOpenQuestions = { id ->
                    navController.navigate(Routes.testQuestionEditor(id))
                },
            )
        }
        composable(
            route = Routes.TEST_QUESTION_EDITOR,
            arguments = listOf(navArgument("testId") { type = NavType.StringType })
        ) {
            TestQuestionEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.TEST_EDITOR_FOR_SCOPE,
            arguments = listOf(
                navArgument("scope") { type = NavType.StringType },
                navArgument("scopeId") { type = NavType.StringType },
            )
        ) {
            TestEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onOpenQuestions = { id ->
                    navController.navigate(Routes.testQuestionEditor(id))
                },
            )
        }
        composable(
            route = Routes.CONTENT_LEVEL,
            arguments = listOf(navArgument("levelId") { type = NavType.StringType })
        ) {
            ContentLevelScreen(
                onBack = { navController.popBackStack() },
                onOpenSection = { levelId, sectionId -> navController.navigate(Routes.contentSection(levelId, sectionId)) }
            )
        }
        composable(
            route = Routes.CONTENT_SECTION,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("sectionId") { type = NavType.StringType }
            )
        ) {
            ContentSectionScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { levelId, requirementId ->
                    navController.navigate(Routes.requirementEditor(levelId, requirementId))
                }
            )
        }

        // ===== Chats =====
        composable(Routes.CHATS_LIST) {
            ChatsListScreen(
                onBack = { navController.popBackStack() },
                onOpenClubChat = { navController.navigate(Routes.CLUB_CHAT) },
                onOpenDM = { dmId, otherUid -> navController.navigate(Routes.dmChat(dmId, otherUid)) },
                onOpenGroup = { groupId -> navController.navigate(Routes.customGroupChat(groupId)) },
                onCreateGroup = { navController.navigate(Routes.CREATE_GROUP_CHAT) },
            )
        }
        composable(Routes.CREATE_GROUP_CHAT) {
            CreateGroupChatScreen(
                onBack = { navController.popBackStack() },
                onCreated = { groupId ->
                    navController.popBackStack()
                    navController.navigate(Routes.customGroupChat(groupId))
                },
            )
        }
        composable(
            route = Routes.CUSTOM_GROUP_CHAT,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) {
            CustomGroupChatScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DM_CHAT,
            arguments = listOf(
                navArgument("dmId") { type = NavType.StringType },
                navArgument("otherUid") { type = NavType.StringType },
            ),
        ) {
            DMChatScreen(onBack = { navController.popBackStack() })
        }

        // ===== Honors =====
        composable(Routes.HONOR_CATALOG) {
            HonorCatalogScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId -> navController.navigate(Routes.honorDetail(honorId)) }
            )
        }
        composable(
            route = Routes.HONOR_DETAIL,
            arguments = listOf(navArgument("honorId") { type = NavType.StringType })
        ) { backStackEntry ->
            val honorId = backStackEntry.arguments?.getString("honorId") ?: ""
            HonorDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenRequirement = { _, requirementId ->
                    navController.navigate(Routes.honorRequirementDetail(honorId, requirementId))
                },
                onOpenTest = { testId ->
                    navController.navigate(Routes.test(testId))
                }
            )
        }
        composable(
            route = Routes.HONOR_REQUIREMENT_DETAIL,
            arguments = listOf(
                navArgument("honorId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val honorId = backStackEntry.arguments?.getString("honorId") ?: ""
            val requirementId = backStackEntry.arguments?.getString("requirementId") ?: ""
            HonorRequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(Routes.fileUpload("HONOR_REQUIREMENT", requirementId)) },
                onOpenBook = { bookId ->
                    navController.navigate(Routes.bookDetail(bookId))
                },
                navBackStackEntry = backStackEntry
            )
        }
        composable(Routes.MY_HONORS) {
            MyHonorsScreen(
                onBack = { navController.popBackStack() },
                onOpenHonor = { honorId -> navController.navigate(Routes.honorDetail(honorId)) }
            )
        }
        composable(
            route = Routes.TEST,
            arguments = listOf(navArgument("testId") { type = NavType.StringType })
        ) {
            TestScreen(onBack = { navController.popBackStack() }, onFinished = { navController.popBackStack() })
        }
        composable(Routes.OPEN_ANSWERS) {
            OpenAnswersScreen(
                onBack = { navController.popBackStack() },
                onOpenAttempt = { attemptId ->
                    navController.navigate(Routes.openAnswerReview(attemptId))
                },
            )
        }
        composable(
            route = Routes.OPEN_ANSWER_REVIEW,
            arguments = listOf(navArgument("attemptId") { type = NavType.StringType })
        ) {
            OpenAnswerReviewScreen(onBack = { navController.popBackStack() })
        }

        // ===== File Upload =====
        composable(
            route = Routes.FILE_UPLOAD,
            arguments = listOf(
                navArgument("contextType") { type = NavType.StringType },
                navArgument("contextId") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            FileUploadScreen(
                onBack = { navController.popBackStack() },
                onSuccess = { result ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(FileUploadResult.SAVED_STATE_KEY, result)
                    navController.popBackStack()
                }
            )
        }

        // ===== Events & Tasks =====
        composable(Routes.EVENTS) {
            EventsScreen(
                onBack = { navController.popBackStack() },
                onOpenEvent = { eventId -> navController.navigate(Routes.eventDetail(eventId)) }
            )
        }
        composable(
            route = Routes.EVENT_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) {
            EventDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenConsents = { eventId ->
                    navController.navigate(Routes.eventConsents(eventId))
                },
                onOpenTest = { testId ->
                    navController.navigate(Routes.test(testId))
                }
            )
        }

        composable(
            route = Routes.EVENT_CONSENTS,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) {
            EventConsentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MY_TASKS) {
            MyTasksScreen(
                onBack = { navController.popBackStack() },
                onCreateTask = { navController.navigate(Routes.CREATE_TASK) },
                onOpenTest = { testId -> navController.navigate(Routes.test(testId)) },
                onCreateTest = { taskId ->
                    navController.navigate(Routes.testEditorForScope("task", taskId))
                }
            )
        }

        // ===== Profile =====
        composable(Routes.TEEN_PROFILE) {
            TeenProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = { navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }

        // ===== Gamification =====
        composable(Routes.DIGITAL_UNIFORM) {
            DigitalUniformScreen(
                onOpenDetail = { navController.navigate(Routes.DIGITAL_UNIFORM_DETAIL) },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DIGITAL_UNIFORM_DETAIL) {
            com.pathfinder.hub.ui.gamification.DigitalUniformDetailScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onNavigateBack = { navController.popBackStack() }) }

        // ===== Challenges =====
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

        // ===== Reports =====
        composable(Routes.REPORT_HISTORY) {
            ReportHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportClick = { reportId -> navController.navigate(Routes.reportDetail(reportId)) },
                onCreateReport = { navController.navigate(Routes.CREATE_REPORT) }
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

        // ===== Chats & Moderation =====
        composable(Routes.CLUB_CHAT) { backStackEntry ->
            ClubChatScreen(
                onBack = { navController.popBackStack() },
                onAttachFile = { clubId -> navController.navigate(Routes.fileUpload("CHAT", clubId)) },
                savedStateHandle = backStackEntry.savedStateHandle
            )
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
                onBack = { navController.popBackStack() },
                savedStateHandle = backStackEntry.savedStateHandle
            )
        }
        composable(Routes.MODERATION) { ModerationScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { navController.popBackStack() }) }
        // ===== Доп. информация =====
        composable(Routes.INFO_HUB) {
            InfoHubScreen(
                onBack = { navController.popBackStack() },
                onOpenCategory = { catId -> navController.navigate(Routes.infoCategory(catId)) },
                onOpenEditor = { catId -> navController.navigate(Routes.infoEditor(catId ?: "other")) }
            )
        }
        composable(
            route = Routes.INFO_CATEGORY,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val catId = backStackEntry.arguments?.getString("categoryId") ?: ""
            InfoCategoryScreen(
                onBack = { navController.popBackStack() },
                onOpenArticle = { cId, aId -> navController.navigate(Routes.infoArticle(cId, aId)) },
                onOpenEditor = { cId -> navController.navigate(Routes.infoEditor(cId)) }
            )
        }
        composable(
            route = Routes.INFO_EDITOR,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("articleId") { type = NavType.StringType }
            )
        ) {
            InfoEditorScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.INFO_ARTICLE,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("articleId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val catId = backStackEntry.arguments?.getString("categoryId") ?: ""
            val artId = backStackEntry.arguments?.getString("articleId") ?: ""
            InfoArticleScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SYNC) { SyncScreen(onBack = { navController.popBackStack() }) }

        composable(
            route = Routes.CLUB_INFO,
            arguments = listOf(navArgument("clubId") { type = NavType.StringType })
        ) {
            ClubInfoScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ARCHIVE) {
            ArchiveScreen(onBack = { navController.popBackStack() })
        }

        // ===== Пользовательские группы (B) =====
        composable(Routes.MANAGE_CUSTOM_GROUPS) {
            ManageCustomGroupsScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { id -> navController.navigate(Routes.customGroupEditor(id)) },
                onCreateGroup = { navController.navigate(Routes.customGroupEditor("new")) },
            )
        }
        composable(
            route = Routes.CUSTOM_GROUP_EDITOR,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            CreateCustomGroupScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        // ===== Sabbath School =====
        composable(Routes.SABBATH_SCHOOL_LIST) {
            val vm: SabbathSchoolListViewModel = hiltViewModel()
            val state by vm.state.collectAsState()
            SabbathSchoolListScreen(
                year = state.year,
                quarter = state.quarter,
                currentLesson = state.currentLesson,
                onLessonClick = { lessonId ->
                    navController.navigate(Routes.sabbathSchool(lessonId))
                },
                onOpenSettings = { navController.navigate(Routes.SABBATH_SCHOOL_SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SABBATH_SCHOOL,
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
            val vm: SabbathSchoolViewModel = hiltViewModel()
            SabbathLessonScreen(viewModel = vm, lessonId = lessonId)
        }
        composable(Routes.SABBATH_SCHOOL_SETTINGS) {
            SabbathSchoolSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}