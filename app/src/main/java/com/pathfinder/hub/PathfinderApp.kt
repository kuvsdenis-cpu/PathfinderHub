package com.pathfinder.hub

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.UserClubSeeder
import com.pathfinder.hub.data.local.worker.BibleSeedWorker
import com.pathfinder.hub.data.local.worker.BookSeedWorker
import com.pathfinder.hub.data.local.worker.PearlSeedWorker
import com.pathfinder.hub.data.local.worker.ReadingPlanSeedWorker
import com.pathfinder.hub.data.local.worker.SeedDatabaseWorker
import com.pathfinder.hub.data.local.worker.UploadLocalBooksWorker
import com.pathfinder.hub.data.archive.PurgeScheduler
import com.pathfinder.hub.data.sync.SyncScheduler
import com.pathfinder.hub.domain.usecase.gamification.SeedAchievementsUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltAndroidApp
class PathfinderApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var purgeScheduler: PurgeScheduler

    @Inject
    lateinit var seedAchievementsUseCase: SeedAchievementsUseCase

    @Inject
    lateinit var userClubSeeder: UserClubSeeder

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught in appScope", e)
        }
    )

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Coil: disk cache 512 МБ. URL картинок ступеней стабильные
     * (публичный бакет) → Coil скачивает один раз и дальше отдаёт с диска.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .build()

    override fun onCreate() {
        super.onCreate()

        // #7: фоновый старт — ничего тяжёлого на главном потоке.
        //
        // Раньше здесь синхронно вызывались:
        //   - initAppCheck() / initFirestore() — тяжёлые Firebase-биндинги;
        //   - syncScheduler.schedulePeriodic() / syncNow() / purgeScheduler.schedule() —
        //     четыре раза поднимали WorkManager.getInstance() на UI-потоке.
        // Теперь всё это уходит в Dispatchers.IO, а на UI-потоке остаётся
        // только регистрация Hilt и возврат из onCreate.
        appScope.launch {
            // Firebase нужен к первому сетевому запросу, не к первому кадру.
            initAppCheck()
            initFirestore()

            // Планировщики WorkManager — тоже фоновая история.
            try {
                syncScheduler.schedulePeriodic()
                syncScheduler.syncNow()
                purgeScheduler.schedule()
            } catch (e: Exception) {
                Log.e(TAG, "Schedulers упали", e)
            }

            // Демо-данные — только в debug (d-1).
            if (BuildConfig.DEBUG) {
                seedDemoData()
            }

            // B1: досид user_clubs из users.clubIds
            try {
                try {
                userClubSeeder.seedIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "UserClubSeeder упал", e)
            }
            } catch (e: Exception) {
                Log.e(TAG, "UserClubSeeder упал", e)
            }

            // Загрузка контента и наблюдение за воркером — как было.
            try {
                seedAchievements()
                scheduleContentUpdate()
                scheduleBibleSeed()
                schedulePearlSeed()
                scheduleReadingPlanSeed()
                scheduleBookSeed()
                scheduleUploadLocalBooks()
            } catch (e: Exception) {
                Log.e(TAG, "schedule* упали", e)
            }
            try {
                observeSeedCompletion()
                observeBibleSeedCompletion()
                observePearlSeedCompletion()
                observeReadingPlanSeedCompletion()
            } catch (e: Exception) {
                Log.e(TAG, "observe* упали", e)
            }
        }
    }

    // ==================== FIREBASE APP CHECK ====================

    private fun initAppCheck() {
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "App Check: Debug provider")
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "App Check: Play Integrity provider")
        }
    }

    // ==================== FIRESTORE ====================

    private fun initFirestore() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100L * 1024 * 1024)
                    .build()
            )
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        Log.d(TAG, "Firestore: офлайн-кэш 100 МБ")
    }

    // ==================== ОБНОВЛЕНИЕ КОНТЕНТА ====================

    private fun scheduleContentUpdate() {
        val request = OneTimeWorkRequestBuilder<SeedDatabaseWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            SeedDatabaseWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d(TAG, "Воркер SeedDatabaseWorker поставлен в очередь (REPLACE)")
    }

    /**
     * Наблюдает за завершением воркера в фоне.
     * НЕ блокирует старт приложения.
     */
    private suspend fun observeSeedCompletion() {
        val workManager = WorkManager.getInstance(this)

        kotlinx.coroutines.delay(500L)

        while (true) {
            val infos = workManager
                .getWorkInfosForUniqueWork(SeedDatabaseWorker.WORK_NAME)
                .get()

            val failed = infos.any {
                it.state == WorkInfo.State.FAILED ||
                        it.state == WorkInfo.State.CANCELLED
            }
            if (failed) {
                Log.e(TAG, "❌ SeedDatabaseWorker завершился неудачей")
                verifyContentLoaded()
                return
            }

            val anyRunning = infos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }

            if (!anyRunning) {
                Log.d(TAG, "✓ SeedDatabaseWorker завершён")
                verifyContentLoaded()
                return
            }

            kotlinx.coroutines.delay(1000L)
        }
    }

    private suspend fun verifyContentLoaded() {
        try {
            val db = AppDatabase.getInstance(this)
            val levelDao = db.levelDao()

            val levels = levelDao.getLevelsCount()
            val requirements = levelDao.getRequirementsCount()

            Log.d(TAG, "📊 ФИНАЛЬНАЯ ПРОВЕРКА: levels=$levels, requirements=$requirements")

            if (requirements == 0) {
                Log.e(TAG, "❌ ВНИМАНИЕ: требования НЕ загружены после воркера!")
            } else {
                Log.d(TAG, "✅ Контент загружен: $requirements требований")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка финальной проверки", e)
        }
    }

    // ==================== ДЕМО-ДАННЫЕ ====================

    private suspend fun seedDemoData() {
        try {
            val db = AppDatabase.getInstance(this)
            val userDao = db.userDao()
            val inviteDao = db.inviteDao()
            val clubDao = db.clubDao()

            val now = Date()
            val in7Days = Date(now.time + 7L * 24 * 3600 * 1000)

            clubDao.upsert(
                ClubEntity(
                    id = "demo_club",
                    conferenceId = "demo_conf",
                    name = "Демо-клуб",
                    logoUrl = null,
                    city = "Москва",
                    region = "Москва",
                    contactEmail = "demo@pathfinder.local",
                    contactPhone = "",
                    foundedAt = null,
                    motto = null,
                    meetingDay = "saturday",
                    startTime = "10:00",
                    endTime = "13:00",
                    location = "Церковь",
                    frequency = "weekly",
                    autoAcceptInvite = true,
                    inviteLifetimeDays = 7,
                    requireParentalConsent = true,
                    maxMembers = null,
                    paymentEnabled = false,
                    membershipFee = null,
                    membershipPeriod = null,
                    paymentMethods = emptyList(),
                    paymentRemindersEnabled = false,
                    autoModerationEnabled = true,
                    reviewWindowHours = 6,
                    escalationHours = 12,
                    defaultReportTemplateId = null,
                    reportPeriods = listOf("month", "quarter", "year"),
                    autoGenerateReports = false,
                    updatedAt = now,
                    updatedBy = "system"
                )
            )

            userDao.upsert(
                UserEntity(
                    id = "director_demo",
                    email = "director@pathfinder.local",
                    emailVerified = true,
                    firstName = "Иван",
                    lastName = "Директоров",
                    role = "director",
                    clubId = "demo_club",
                    conferenceId = "demo_conf",
                    birthDate = now,
                    avatar = null,
                    parentEmail = null,
                    parentalConsentStatus = "approved",
                    parentalConsentRequestedAt = null,
                    parentalConsentApprovedAt = null,
                    accessLevel = "full",
                    status = "active",
                    deletionRequestedBy = null,
                    deletionRequestedAt = null,
                    anonymizedAt = null,
                    permanentDeletionAt = null,
                    onboardingCompleted = true,
                    createdAt = now,
                    lastLoginAt = null
                )
            )

            inviteDao.upsert(
                InviteEntity(
                    id = "invite_001",
                    code = "ABCD-2345",
                    clubId = "demo_club",
                    role = "teen",
                    createdBy = "director_demo",
                    createdAt = now,
                    expiresAt = in7Days,
                    status = "active",
                    usedBy = null,
                    usedAt = null
                )
            )

            inviteDao.upsert(
                InviteEntity(
                    id = "invite_002",
                    code = "PARENT-99",
                    clubId = "demo_club",
                    role = "parent",
                    createdBy = "director_demo",
                    createdAt = now,
                    expiresAt = in7Days,
                    status = "active",
                    usedBy = null,
                    usedAt = null
                )
            )

            Log.d(TAG, "Демо-данные созданы")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка создания демо-данных", e)
        }
    }

    // ==================== ЗАГРУЗКА АЧИВОК ====================

    private suspend fun seedAchievements() {
        try {
            seedAchievementsUseCase()
            Log.d(TAG, "Ачивки загружены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки ачивок", e)
        }
    }

    // ==================== БИБЛИЯ ====================

    private fun scheduleUploadLocalBooks() {
        val r = OneTimeWorkRequestBuilder<UploadLocalBooksWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            UploadLocalBooksWorker.WORK_NAME, ExistingWorkPolicy.KEEP, r)
        Log.d(TAG, "UploadLocalBooksWorker → KEEP")
    }
    private fun scheduleBibleSeed() {
        val request = OneTimeWorkRequestBuilder<BibleSeedWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            BibleSeedWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Воркер BibleSeedWorker поставлен в очередь (KEEP)")
    }

    private suspend fun observeBibleSeedCompletion() {
        val workManager = WorkManager.getInstance(this)
        kotlinx.coroutines.delay(500L)

        while (true) {
            val infos = workManager
                .getWorkInfosForUniqueWork(BibleSeedWorker.WORK_NAME)
                .get()

            val failed = infos.any {
                it.state == WorkInfo.State.FAILED ||
                        it.state == WorkInfo.State.CANCELLED
            }
            if (failed) {
                Log.e(TAG, "❌ BibleSeedWorker завершился неудачей")
                verifyBibleLoaded()
                return
            }

            val anyRunning = infos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
            if (!anyRunning) {
                Log.d(TAG, "✓ BibleSeedWorker завершён")
                verifyBibleLoaded()
                return
            }

            kotlinx.coroutines.delay(1000L)
        }
    }

    private suspend fun verifyBibleLoaded() {
        try {
            val db = AppDatabase.getInstance(this)
            val count = db.bibleDao().getVerseCount("rst")
            Log.d(TAG, "📊 Bible verses in Room: $count")
            if (count == 0) {
                Log.e(TAG, "❌ ВНИМАНИЕ: стихи Библии НЕ загружены!")
            } else {
                Log.d(TAG, "✅ Библия загружена: $count стихов")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки Библии", e)
        }
    }

    // ==================== КНИГИ ====================

    private fun scheduleBookSeed() {
        val request = OneTimeWorkRequestBuilder<BookSeedWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            BookSeedWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Воркер BookSeedWorker поставлен в очередь (KEEP)")
    }

    // ==================== ЖЕМЧУЖИНЫ (B3.1) ====================

    private fun schedulePearlSeed() {
        val request = OneTimeWorkRequestBuilder<PearlSeedWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            PearlSeedWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Воркер PearlSeedWorker поставлен в очередь (KEEP)")
    }

    private suspend fun observePearlSeedCompletion() {
        val workManager = WorkManager.getInstance(this)
        kotlinx.coroutines.delay(500L)

        while (true) {
            val infos = workManager
                .getWorkInfosForUniqueWork(PearlSeedWorker.WORK_NAME)
                .get()

            val failed = infos.any {
                it.state == WorkInfo.State.FAILED ||
                        it.state == WorkInfo.State.CANCELLED
            }
            if (failed) {
                Log.e(TAG, "PearlSeedWorker завершился неудачей")
                return
            }

            val anyRunning = infos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
            if (!anyRunning) {
                Log.d(TAG, "PearlSeedWorker завершён")
                return
            }

            kotlinx.coroutines.delay(1000L)
        }
    }

    // ==================== ПЛАН ЧТЕНИЯ (B4.1) ====================

    private fun scheduleReadingPlanSeed() {
        val request = OneTimeWorkRequestBuilder<ReadingPlanSeedWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            ReadingPlanSeedWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Воркер ReadingPlanSeedWorker поставлен в очередь (KEEP)")
    }

    private suspend fun observeReadingPlanSeedCompletion() {
        val workManager = WorkManager.getInstance(this)
        kotlinx.coroutines.delay(500L)
        while (true) {
            val infos = workManager
                .getWorkInfosForUniqueWork(ReadingPlanSeedWorker.WORK_NAME)
                .get()
            val failed = infos.any {
                it.state == WorkInfo.State.FAILED ||
                        it.state == WorkInfo.State.CANCELLED
            }
            if (failed) {
                Log.e(TAG, "ReadingPlanSeedWorker завершился неудачей")
                return
            }
            val anyRunning = infos.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
            if (!anyRunning) {
                Log.d(TAG, "ReadingPlanSeedWorker завершён")
                return
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    companion object {
        private const val TAG = "PathfinderApp"
    }
}