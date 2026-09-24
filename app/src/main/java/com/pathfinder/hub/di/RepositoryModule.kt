package com.pathfinder.hub.di

import com.pathfinder.hub.data.local.dao.*
import com.pathfinder.hub.data.repository.*
import com.pathfinder.hub.data.sync.SyncQueueManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // ---------- Репозитории с SyncQueueManager ----------
    @Provides @Singleton
    fun provideUserRepo(d: UserDao, syncQueueManager: SyncQueueManager) =
        UserRepository(d, syncQueueManager)

    @Provides @Singleton
    fun provideClubRepo(d: ClubDao, syncQueueManager: SyncQueueManager) =
        ClubRepository(d, syncQueueManager)

    @Provides @Singleton
    fun provideEventRepo(d: EventDao, syncQueueManager: SyncQueueManager) =
        EventRepository(d, syncQueueManager)

    @Provides @Singleton
    fun provideTaskRepo(d: TaskDao, syncQueueManager: SyncQueueManager) =
        TaskRepository(d, syncQueueManager)

    @Provides @Singleton
    fun provideLevelRepo(d: LevelDao, syncQueueManager: SyncQueueManager) =
        LevelRepository(d, syncQueueManager)

    @Provides @Singleton
    fun provideHonorRepo(d: HonorDao, syncQueueManager: SyncQueueManager) =
        HonorRepository(d, syncQueueManager)

    // ---------- Репозитории без SyncQueueManager (не тронуты) ----------
    @Provides @Singleton fun provideAchievementRepo(d: AchievementDao) = AchievementRepository(d)
    @Provides @Singleton fun provideModerationRepo(d: ModerationDao) = ModerationRepository(d)
    @Provides @Singleton fun provideNotificationRepo(d: NotificationDao) = NotificationRepository(d)
    @Provides @Singleton fun provideSyncRepo(d: SyncDao) = SyncRepository(d)
    @Provides @Singleton fun provideParentRepo(d: ParentDao) = ParentRepository(d)
    @Provides @Singleton fun provideReportRepo(d: ReportDao) = ReportRepository(d)
    @Provides @Singleton fun provideAuditRepo(d: AuditDao) = AuditRepository(d)
    @Provides @Singleton fun provideSettingsRepo(d: SettingsDao) = SettingsRepository(d)
    @Provides @Singleton fun provideNewspaperRepo(d: NewspaperDao) = NewspaperRepository(d)
    @Provides @Singleton fun provideInviteRepo(d: InviteDao) = InviteRepository(d)
}