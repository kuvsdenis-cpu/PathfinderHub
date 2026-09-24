package com.pathfinder.hub.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pathfinder.hub.data.local.dao.*
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import com.pathfinder.hub.data.local.entity.audit.SecurityLogEntity
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.local.entity.auth.OnboardingProgressEntity
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.auth.TemporaryPasswordEntity
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.ConferenceEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.gamification.ActivityLevelEntity
import com.pathfinder.hub.data.local.entity.gamification.CertificateEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.local.entity.gamification.ReputationEntity
import com.pathfinder.hub.data.local.entity.gamification.ShareCardEntity
import com.pathfinder.hub.data.local.entity.gamification.TeamChallengeProgressEntity
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
import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.local.entity.learning.MemoryVerseEntity
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import com.pathfinder.hub.data.local.entity.newspaper.CachedNewspaperEntity
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperIssueEntity
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperModerationEntity
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperPublicationEntity
import com.pathfinder.hub.data.local.entity.newspaper.NewspaperTemplateEntity
import com.pathfinder.hub.data.local.entity.notifications.FCMTokenEntity
import com.pathfinder.hub.data.local.entity.notifications.NotificationAnalyticsEntity
import com.pathfinder.hub.data.local.entity.notifications.NotificationEntity
import com.pathfinder.hub.data.local.entity.notifications.NotificationPreferencesEntity
import com.pathfinder.hub.data.local.entity.offline.CacheMetadataEntity
import com.pathfinder.hub.data.local.entity.offline.CacheSettingsEntity
import com.pathfinder.hub.data.local.entity.offline.MediaSyncQueueEntity
import com.pathfinder.hub.data.local.entity.offline.MergeRuleEntity
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.offline.SyncSettingsEntity
import com.pathfinder.hub.data.local.entity.offline.SyncStatusEntity
import com.pathfinder.hub.data.local.entity.parent.PWAConfigEntity
import com.pathfinder.hub.data.local.entity.parent.PWAInstallEventEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMeetingEntity
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.local.entity.planning.AppealEntity
import com.pathfinder.hub.data.local.entity.planning.AssignmentEntity
import com.pathfinder.hub.data.local.entity.planning.CommentEntity
import com.pathfinder.hub.data.local.entity.planning.ConfirmationEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.EventHonorEntity
import com.pathfinder.hub.data.local.entity.planning.EventOccurrenceEntity
import com.pathfinder.hub.data.local.entity.planning.ModerationHistoryEntity
import com.pathfinder.hub.data.local.entity.planning.ModerationRequestEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.local.entity.reports.AnalyticsSnapshotEntity
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.local.entity.reports.ReportHistoryEntity
import com.pathfinder.hub.data.local.entity.reports.ReportSignatureEntity
import com.pathfinder.hub.data.local.entity.reports.ReportTemplateEntity
import com.pathfinder.hub.data.local.entity.settings.ClubSettingsEntity
import com.pathfinder.hub.data.local.entity.settings.ConferenceSettingsEntity
import com.pathfinder.hub.data.local.entity.settings.SystemSettingsEntity
import com.pathfinder.hub.data.local.entity.settings.UserSettingsEntity
import com.pathfinder.hub.data.local.migration.ALL_MIGRATIONS

@Database(
    entities = [
        // Core
        ConferenceEntity::class,
        ClubEntity::class,
        UserEntity::class,

        // Auth
        InviteEntity::class,
        OnboardingProgressEntity::class,
        ParentChildLinkEntity::class,
        TemporaryPasswordEntity::class,

        // Learning
        LevelEntity::class,
        LevelSectionEntity::class,
        LevelRequirementEntity::class,
        LevelProgressEntity::class,
        LevelCompletionEntity::class,
        HonorCategoryEntity::class,
        HonorEntity::class,
        HonorRequirementEntity::class,
        HonorProgressEntity::class,
        HonorCompletionEntity::class,
        TestQuestionEntity::class,
        TestAttemptEntity::class,
        OpenAnswerReviewEntity::class,
        HonorVersionEntity::class,
        ContentVersionEntity::class,
        HonorDraftEntity::class,
        BookEntity::class,
        BookReportEntity::class,
        MemoryVerseEntity::class,
        VerseProgressEntity::class,
        LeaderChecklistEntity::class,

        // Gamification
        AchievementEntity::class,
        AchievementProgressEntity::class,
        DigitalUniformEntity::class,
        ChallengeEntity::class,
        ChallengeProgressEntity::class,
        TeamChallengeProgressEntity::class,
        HomeworkEntity::class,
        ActivityLevelEntity::class,
        ReputationEntity::class,
        CertificateEntity::class,
        ShareCardEntity::class,

        // Planning
        EventEntity::class,
        EventOccurrenceEntity::class,
        EventHonorEntity::class,
        TaskEntity::class,
        AssignmentEntity::class,
        ConfirmationEntity::class,
        CommentEntity::class,
        ModerationRequestEntity::class,
        AppealEntity::class,
        ModerationHistoryEntity::class,

        // Notifications
        NotificationEntity::class,
        NotificationPreferencesEntity::class,
        FCMTokenEntity::class,
        NotificationAnalyticsEntity::class,

        // Offline
        SyncQueueItemEntity::class,
        SyncConflictEntity::class,
        MediaSyncQueueEntity::class,
        CacheMetadataEntity::class,
        CacheSettingsEntity::class,
        MergeRuleEntity::class,
        SyncSettingsEntity::class,
        SyncStatusEntity::class,

        // Parent
        PaymentEntity::class,
        ParentMessageEntity::class,
        ParentMeetingEntity::class,
        PWAConfigEntity::class,
        PWAInstallEventEntity::class,

        // Reports
        ReportTemplateEntity::class,
        ReportEntity::class,
        ReportSignatureEntity::class,
        ReportHistoryEntity::class,
        AnalyticsSnapshotEntity::class,

        // Audit
        AuditLogEntity::class,
        SecurityLogEntity::class,

        // Settings
        UserSettingsEntity::class,
        ClubSettingsEntity::class,
        ConferenceSettingsEntity::class,
        SystemSettingsEntity::class,

        // Newspaper
        NewspaperIssueEntity::class,
        NewspaperTemplateEntity::class,
        NewspaperModerationEntity::class,
        NewspaperPublicationEntity::class,
        CachedNewspaperEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Core
    abstract fun userDao(): UserDao
    abstract fun clubDao(): ClubDao

    // Auth
    abstract fun inviteDao(): InviteDao

    // Planning
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao

    // Learning
    abstract fun levelDao(): LevelDao
    abstract fun honorDao(): HonorDao

    // Gamification
    abstract fun achievementDao(): AchievementDao

    // Moderation
    abstract fun moderationDao(): ModerationDao

    // Notifications
    abstract fun notificationDao(): NotificationDao

    // Offline
    abstract fun syncDao(): SyncDao

    // Parent
    abstract fun parentDao(): ParentDao

    // Reports
    abstract fun reportDao(): ReportDao

    // Audit
    abstract fun auditDao(): AuditDao

    // Settings
    abstract fun settingsDao(): SettingsDao

    // Newspaper
    abstract fun newspaperDao(): NewspaperDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pathfinder_hub.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}