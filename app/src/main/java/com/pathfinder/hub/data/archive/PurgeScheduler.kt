package com.pathfinder.hub.data.archive

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Планирует PurgeWorker раз в сутки.
 */
@Singleton
class PurgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Purge: удаление просроченного
        val purgeRequest = PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PurgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest,
        )

        // Auto-Archive: комментарии старше 14 дней
        val autoArchiveRequest = PeriodicWorkRequestBuilder<AutoArchiveWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoArchiveWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            autoArchiveRequest,
        )
    }
}
