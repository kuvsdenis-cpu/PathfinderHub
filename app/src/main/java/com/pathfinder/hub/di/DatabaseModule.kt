package com.pathfinder.hub.di
import android.content.Context
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase = AppDatabase.getInstance(ctx)

    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideClubDao(db: AppDatabase): ClubDao = db.clubDao()
    @Provides fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideLevelDao(db: AppDatabase): LevelDao = db.levelDao()
    @Provides fun provideHonorDao(db: AppDatabase): HonorDao = db.honorDao()
    @Provides fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
    @Provides fun provideModerationDao(db: AppDatabase): ModerationDao = db.moderationDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideSyncDao(db: AppDatabase): SyncDao = db.syncDao()
    @Provides fun provideParentDao(db: AppDatabase): ParentDao = db.parentDao()
    @Provides fun provideReportDao(db: AppDatabase): ReportDao = db.reportDao()
    @Provides fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideNewspaperDao(db: AppDatabase): NewspaperDao = db.newspaperDao()
    @Provides fun provideInviteDao(db: AppDatabase): InviteDao = db.inviteDao()

    @Provides fun provideCustomGroupDao(db: AppDatabase): CustomGroupDao = db.customGroupDao()

    @Provides fun provideUserClubDao(db: AppDatabase): UserClubDao = db.userClubDao()

    // ---------- Learning: материалы и задания ----------
    @Provides fun provideRequirementMaterialDao(db: AppDatabase): RequirementMaterialDao = db.requirementMaterialDao()
    @Provides fun provideRequirementTaskDao(db: AppDatabase): RequirementTaskDao = db.requirementTaskDao()
    @Provides fun provideRequirementTaskAnswerDao(db: AppDatabase): RequirementTaskAnswerDao = db.requirementTaskAnswerDao()
}