package com.pathfinder.hub.ui.navigation

object Routes {
    // === Auth ===
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ONBOARDING = "onboarding/{role}/{userId}"
    const val ROLE_HOME = "role_home"

    // === Director ===
    const val NOTIFICATIONS = "notifications"
    const val DIRECTOR_HOME = "director_home"
    const val DIRECTOR_INVITES = "director_invites"
    const val CLUB_MEMBERS = "club_members"
    const val APPROVALS = "approvals"
    const val DIRECTOR_REPORTS = "director_reports"
    const val CLUB_EVENTS = "club_events"
    const val CLUB_SETTINGS = "club_settings"
    const val CREATE_EVENT = "create_event"
    const val CREATE_REPORT = "create_report"
    const val CREATE_TASK = "create_task"

    // === Чаты и Модерация ===
    const val CLUB_CHAT = "club_chat"
    const val GROUP_CHAT = "group_chat/{targetType}/{targetId}"
    const val MODERATION = "moderation"

    // === Role-specific home ===
    const val TEEN_HOME = "teen_home"
    const val PARENT_HOME = "parent_home"
    const val INSTRUCTOR_HOME = "instructor_home"
    const val SECRETARY_HOME = "secretary_home"
    const val CONFERENCE_HOME = "conference_home"

    // === Teen - Levels ===
    const val MY_LEVELS = "my_levels"
    const val LEVEL_DETAIL = "level_detail/{levelId}"
    const val REQUIREMENT_DETAIL = "requirement_detail/{requirementId}"

    // === Teen - Honors ===
    const val HONOR_CATALOG = "honor_catalog"
    const val HONOR_DETAIL = "honor_detail/{honorId}"
    const val HONOR_REQUIREMENT_DETAIL = "honor_requirement_detail/{requirementId}"
    const val MY_HONORS = "my_honors"
    const val TEST_SCREEN = "test_screen/{honorId}"
    const val REPORT_UPLOAD = "report_upload/{requirementId}"

    // === Teen - Events & Tasks ===
    const val EVENTS = "events"
    const val EVENT_DETAIL = "event_detail/{eventId}"
    const val MY_TASKS = "my_tasks"

    // === Teen - Profile ===
    const val TEEN_PROFILE = "teen_profile"

    // === Teen - Gamification ===
    const val DIGITAL_UNIFORM = "digital_uniform"
    const val ACHIEVEMENTS = "achievements"

    // === Teen - Challenges ===
    const val CHALLENGES = "challenges"
    const val CHALLENGE_DETAIL = "challenge_detail/{challengeId}"

    // === Reports ===
    const val REPORT_HISTORY = "report_history"
    const val REPORT_DETAIL = "report_detail/{reportId}"

    // === Sync ===
    const val SYNC = "sync"

    // === Helpers ===
    fun onboarding(role: String, userId: String) = "onboarding/$role/$userId"
    fun levelDetail(levelId: String) = "level_detail/$levelId"
    fun requirementDetail(requirementId: String) = "requirement_detail/$requirementId"
    fun honorDetail(honorId: String) = "honor_detail/$honorId"
    fun honorRequirementDetail(requirementId: String) = "honor_requirement_detail/$requirementId"
    fun testScreen(honorId: String) = "test_screen/$honorId"
    fun reportUpload(requirementId: String) = "report_upload/$requirementId"
    fun eventDetail(eventId: String) = "event_detail/$eventId"
    fun challengeDetail(challengeId: String) = "challenge_detail/$challengeId"
    fun reportDetail(reportId: String) = "report_detail/$reportId"
    fun groupChat(targetType: String, targetId: String) = "group_chat/$targetType/$targetId"
}