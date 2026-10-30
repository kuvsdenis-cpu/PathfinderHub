package com.pathfinder.hub.ui.navigation

import android.net.Uri

object Routes {
    // === Auth ===
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ONBOARDING = "onboarding/{role}/{userId}"
    const val ROLE_HOME = "role_home"

    // === Notifications ===
    const val NOTIFICATIONS = "notifications"

    // === Director ===
    const val DIRECTOR_HOME = "director_home"
    const val DIRECTOR_PEOPLE = "director_people"
    const val DIRECTOR_LEARNING = "director_learning"
    const val DIRECTOR_PLANNING = "director_planning"
    const val DIRECTOR_COMMUNICATIONS = "director_communications"
    const val DIRECTOR_FINANCE = "director_finance"
    const val DIRECTOR_MANAGEMENT = "director_management"
    const val DIRECTOR_INVITES = "director_invites"
    const val CLUB_MEMBERS = "club_members"
    const val APPROVALS = "approvals"
    const val DIRECTOR_REPORTS = "director_reports"
    const val CLUB_EVENTS = "club_events"
    const val CLUB_SETTINGS = "club_settings"
    const val CREATE_EVENT = "create_event"
    const val CREATE_REPORT = "create_report"
    const val CREATE_TASK = "create_task"
    const val CREATE_HOMEWORK = "create_homework"
    const val REVIEW_HOMEWORK = "review_homework"
    const val CREATE_CLUB = "create_club"
    const val STAFF = "staff"
    const val SCOUT_DETAIL = "scout_detail/{scoutId}"
    fun scoutDetail(scoutId: String) = "scout_detail/$scoutId"
    const val MANAGE_PERMISSIONS = "manage_permissions/{userId}"
    fun managePermissions(userId: String) = "manage_permissions/$userId"

    // === Payments ===
    const val DIRECTOR_PAYMENTS = "director_payments"
    const val CREATE_PAYMENT = "create_payment"
    const val PARENT_PAYMENTS = "parent_payments"
    const val PARENT_MESSAGES = "parent_messages"
    const val PARENT_MEETINGS = "parent_meetings"
    const val CREATE_PARENT_MEETING = "create_parent_meeting"
    const val CROSS_CLUB = "cross_club"
    const val CROSS_CLUB_TEEN = "cross_club_teen"
    const val CONFERENCE_EVENTS = "conference_events"
    const val CREATE_CONFERENCE_EVENT = "create_conference_event"
    const val CONFERENCE_ANALYTICS = "conference_analytics"
    const val PARENT_CONSENTS = "parent_consents"
    const val CHILD_PROGRESS = "child_progress/{childId}"
    fun childProgress(childId: String) = "child_progress/$childId"

    // === Чаты и Модерация ===
    const val CLUB_CHAT = "club_chat"
    const val CHATS_LIST = "chats_list"
    const val DM_CHAT = "dm_chat/{dmId}/{otherUid}"
    const val CREATE_GROUP_CHAT = "create_group_chat"
    const val CUSTOM_GROUP_CHAT = "custom_group_chat/{groupId}"
    fun customGroupChat(groupId: String) = "custom_group_chat/$groupId"
    fun dmChat(dmId: String, otherUid: String) = "dm_chat/$dmId/$otherUid"
    const val GROUP_CHAT = "group_chat/{targetType}/{targetId}"
    const val MODERATION = "moderation"

    // === Role-specific home ===
    const val TEEN_HOME = "teen_home"
    const val PARENT_HOME = "parent_home"
    const val INSTRUCTOR_HOME = "instructor_home"
    const val SECRETARY_HOME = "secretary_home"
    const val CONFERENCE_HOME = "conference_home"
    const val CONFERENCE_SECRETARY_HOME = "conference_secretary_home"
    const val CONFERENCE_CLUBS = "conference_clubs"
    const val CONFERENCE_MEMBERS = "conference_members"
    const val CONFERENCE_INVITES = "conference_invites"

    // === Teen - Levels ===
    const val MY_LEVELS = "my_levels"
    const val LEVEL_DETAIL = "level_detail/{levelId}"
    const val REQUIREMENT_DETAIL = "requirement_detail/{levelId}/{requirementId}"
    const val REQUIREMENT_EDITOR = "requirement_editor/{levelId}/{requirementId}"

    // === Content Manager (директор) ===
    const val CONTENT_MANAGER = "content_manager"
    const val CONTENT_LEVEL = "content_level/{levelId}"
    const val CONTENT_SECTION = "content_section/{levelId}/{sectionId}"
    const val CONTENT_HONOR = "content_honor/{honorId}"
    fun contentLevel(levelId: String) = "content_level/$levelId"
    fun contentSection(levelId: String, sectionId: String) = "content_section/$levelId/$sectionId"
    fun contentHonor(honorId: String) = "content_honor/$honorId"
    fun requirementEditor(levelId: String, requirementId: String) =
        "requirement_editor/$levelId/$requirementId"

    // === Teen - Honors ===
    const val HONOR_CATALOG = "honor_catalog"
    const val HONOR_DETAIL = "honor_detail/{honorId}"
    const val HONOR_REQUIREMENT_DETAIL = "honor_requirement_detail/{honorId}/{requirementId}"
    const val MY_HONORS = "my_honors"
    const val TEST = "test/{testId}"
    const val OPEN_ANSWERS = "open_answers"
    const val OPEN_ANSWER_REVIEW = "open_answer_review/{attemptId}"
    const val TEST_EDITOR = "test_editor/{testId}"
    const val TEST_EDITOR_FOR_SCOPE = "test_editor_for_scope/{scope}/{scopeId}"
    const val TEST_QUESTION_EDITOR = "test_question_editor/{testId}"

    // === Универсальная загрузка файлов ===
    const val FILE_UPLOAD = "file_upload/{contextType}/{contextId}"

    // === Teen - Events & Tasks ===
    const val EVENTS = "events"
    const val EVENT_DETAIL = "event_detail/{eventId}"
    const val EVENT_CONSENTS = "event_consents/{eventId}"
    fun eventConsents(eventId: String) = "event_consents/$eventId"
    const val MY_TASKS = "my_tasks"

    // === Teen - Profile ===
    const val TEEN_PROFILE = "teen_profile"
    const val SETTINGS = "settings"

    // === Teen - Gamification ===
    const val DIGITAL_UNIFORM = "digital_uniform"
    const val DIGITAL_UNIFORM_DETAIL = "digital_uniform/detail"
    const val ACHIEVEMENTS = "achievements"

    // === Teen - Challenges ===
    const val CHALLENGES = "challenges"
    const val CHALLENGE_DETAIL = "challenge_detail/{challengeId}"

    // === Certificates ===
    const val MY_CERTIFICATES = "my_certificates"

    // === QR ===
    const val QR_SCANNER = "qr_scanner"
    const val MY_STATIONS = "my_stations"
    const val CREATE_STATION = "create_station?questId={questId}"

    fun createStation(questId: String?) = "create_station?questId=${questId ?: ""}"
    const val QUEST_MANAGE = "quests"
    const val QUEST_EDITOR = "quest_editor/{questId}"
    fun questEditor(questId: String) = "quest_editor/$questId"
    const val QUEST_VIEW = "quest_view/{questId}"
    fun questView(questId: String) = "quest_view/$questId"
    const val QUEST_DETAIL = "quest_detail/{questId}"
    fun questDetail(questId: String) = "quest_detail/$questId"

    // === Activity ===
    const val ACTIVITY = "activity"

    // === Reports ===
    const val REPORT_HISTORY = "report_history"
    const val REPORT_DETAIL = "report_detail/{reportId}"

    // === Club Info ===
    const val CLUB_INFO = "club_info/{clubId}"
    fun clubInfo(clubId: String) = "club_info/$clubId"

    // === Archive ===
    const val ARCHIVE = "archive"

    // === Deleted clubs (trash) ===
    const val DELETED_CLUBS = "deleted_clubs"

    // === Пользовательские группы (B) ===
    const val MANAGE_CUSTOM_GROUPS = "manage_custom_groups"
    const val CUSTOM_GROUP_EDITOR = "custom_group_editor/{groupId}"
    fun customGroupEditor(groupId: String) = "custom_group_editor/$groupId"

    // === Доп. информация ===
    const val INFO_HUB = "info_hub"
    const val INFO_CATEGORY = "info_category/{categoryId}"
    const val INFO_ARTICLE = "info_article/{categoryId}/{articleId}"
    const val INFO_EDITOR = "info_editor/{categoryId}/{articleId}"
    fun infoEditor(categoryId: String, articleId: String? = null) = "info_editor/$categoryId/${articleId ?: "new"}"
    fun infoCategory(categoryId: String) = "info_category/$categoryId"
    fun infoArticle(categoryId: String, articleId: String) = "info_article/$categoryId/$articleId"

    // === Sync ===
    const val SYNC = "sync"

    // === Books ===
    const val BOOKS = "books"
    const val BOOK_DETAIL = "books/detail/{bookId}"
    fun bookDetail(bookId: String) = "books/detail/${Uri.encode(bookId)}"

    const val BOOK_READER = "books/reader/{bookId}"
    const val CREATE_BOOK = "books/create"
    fun bookReader(bookId: String) = "books/reader/${Uri.encode(bookId)}"

    // === Bible (B2) ===
    const val BIBLE = "bible"
    const val BIBLE_BOOKMARKS = "bible/bookmarks"
    const val BIBLE_BOOK = "bible/book/{bookId}"
    const val BIBLE_CHAPTER = "bible/chapter/{bookId}/{chapter}?verse={verseId}"
    const val BIBLE_SEARCH = "bible/search"

    fun bibleBook(bookId: String) = "bible/book/${Uri.encode(bookId)}"
    fun bibleChapter(bookId: String, chapter: Int, verseId: String? = null): String {
        val base = "bible/chapter/${Uri.encode(bookId)}/$chapter"
        return if (verseId != null) "$base?verse=${Uri.encode(verseId)}" else base
    }

    // === Pearls (B3.1) ===
    const val PEARLS = "pearls"
    const val PEARL_DETAIL = "pearls/detail/{pearlId}"
    const val PEARL_TRAINING = "pearls/training/{pearlId}"
    fun pearlDetail(pearlId: String) = "pearls/detail/${Uri.encode(pearlId)}"
    fun pearlTraining(pearlId: String) = "pearls/training/${Uri.encode(pearlId)}"
    const val PEARLS_ADMIN = "pearls_admin"

    // === Reading Plan (B4.1) ===
    const val READING_PLANS = "reading_plans"
    const val READING_PLAN_DETAIL = "reading_plans/{scheduleId}"
    const val READING_PLAN_DAY = "reading_plans/day/{dayId}"
    const val READING_PLANS_ADMIN = "reading_plans_admin"
    fun readingPlanDetail(scheduleId: String) = "reading_plans/${Uri.encode(scheduleId)}"
    fun readingPlanDay(dayId: String) = "reading_plans/day/${Uri.encode(dayId)}"

    // === Sabbath School ===
    const val SABBATH_SCHOOL_LIST = "sabbath_school_list"
    const val SABBATH_SCHOOL = "sabbath_school/{lessonId}"
    const val SABBATH_SCHOOL_SETTINGS = "sabbath_school_settings"
    fun sabbathSchool(lessonId: String) = "sabbath_school/$lessonId"

    // === Helpers ===


    /**
     * Целевой home-экран для роли.
     * Возвращает null, если роль неизвестна — вызывающий должен
     * показать ошибку, НЕ отправлять на teen.
     */
    fun homeForRole(role: String?): String? = when (role) {
        "director" -> DIRECTOR_HOME
        "instructor" -> INSTRUCTOR_HOME
        "secretary" -> SECRETARY_HOME
        "parent" -> PARENT_HOME
        "conference_admin" -> CONFERENCE_HOME
        "conference_secretary" -> CONFERENCE_SECRETARY_HOME
        "teen" -> TEEN_HOME
        else -> null
    }

    fun onboarding(role: String, userId: String) = "onboarding/$role/$userId"
    fun levelDetail(levelId: String) = "level_detail/$levelId"
    fun requirementDetail(levelId: String, requirementId: String) =
        "requirement_detail/$levelId/$requirementId"
    fun honorDetail(honorId: String) = "honor_detail/$honorId"
    fun honorRequirementDetail(honorId: String, requirementId: String) =
        "honor_requirement_detail/$honorId/$requirementId"
    fun test(testId: String) = "test/$testId"
    fun openAnswerReview(attemptId: String) = "open_answer_review/$attemptId"
    fun testEditor(testId: String) = "test_editor/$testId"
    fun testEditorForScope(scope: String, scopeId: String) =
        "test_editor_for_scope/$scope/${Uri.encode(scopeId)}"
    fun testQuestionEditor(testId: String) = "test_question_editor/$testId"
    fun fileUpload(contextType: String, contextId: String = "") =
        "file_upload/$contextType/${Uri.encode(contextId)}"
    fun eventDetail(eventId: String) = "event_detail/$eventId"
    fun challengeDetail(challengeId: String) = "challenge_detail/$challengeId"
    fun reportDetail(reportId: String) = "report_detail/$reportId"
    fun groupChat(targetType: String, targetId: String) = "group_chat/$targetType/$targetId"
}
