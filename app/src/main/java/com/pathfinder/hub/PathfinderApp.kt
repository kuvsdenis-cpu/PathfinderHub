package com.pathfinder.hub

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import com.pathfinder.hub.data.local.worker.SeedDatabaseWorker
import com.pathfinder.hub.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltAndroidApp
class PathfinderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initAppCheck()
        initFirestore()
        seedDemoData()            // временно — для тестирования входа
        scheduleContentUpdate()   // обновление контента из assets
        syncScheduler.schedulePeriodic()   // фоновая синхронизация с Firestore
        syncScheduler.syncNow()            // ВРЕМЕННО: разобрать очередь сразу при старте
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
        Log.d(TAG, "Запущена проверка обновления контента")
    }

    // ==================== ДЕМО-ДАННЫЕ (ВРЕМЕННО) ====================
    // УДАЛИТЬ после тестирования входа

    private fun seedDemoData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(this@PathfinderApp)
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

                Log.d(TAG, "Демо-данные созданы: директор, клуб, 2 инвайта")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка создания демо-данных", e)
            }
        }
    }

    companion object {
        private const val TAG = "PathfinderApp"
    }
}