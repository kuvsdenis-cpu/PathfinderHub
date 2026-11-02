package com.pathfinder.hub.data.archive

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pathfinder.hub.data.repository.ModerationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Раз в сутки архивирует комментарии старше 14 дней.
 *
 * В активном UI остаются только последние 14 дней.
 * В архиве — до 90 дней, потом удаляются PurgeWorker-ом.
 */
@HiltWorker
class AutoArchiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val moderationRepository: ModerationRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val archived = moderationRepository.autoArchiveOldComments(daysOld = 14)
            Log.d(TAG, "AutoArchive done: comments archived = $archived")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AutoArchive failed", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "AutoArchiveWorker"
        const val WORK_NAME = "auto_archive"
    }
}
