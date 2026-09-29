package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pathfinder.hub.data.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date

/**
 * Фоновая задача, которая каждые 30 минут проверяет комментарии:
 *  - Если статус = "published" И reviewDeadline < now
 *  - И ручная модерация НЕ проведена (moderatedBy = null)
 *  - Меняет статус на "hidden"
 *
 * Это обеспечивает правило: "6 часов на ручную модерацию, потом скрытие".
 */
@HiltWorker
class ReviewDeadlineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val moderationDao = db.moderationDao()
            val now = System.currentTimeMillis()

            // Получаем все опубликованные комментарии с истёкшим дедлайном
            val pendingComments = moderationDao.observePendingComments()
            // observePendingComments возвращает только pending_review/draft,
            // поэтому нам нужен отдельный запрос для published с истёкшим дедлайном.
            // Добавим его в DAO на следующем шаге.

            // Пока используем прямой SQL через Room query в DAO
            val hiddenCount = moderationDao.hideExpiredComments(now)
            Log.d(TAG, "Скрыто просроченных комментариев: $hiddenCount")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в ReviewDeadlineWorker", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "ReviewDeadlineWorker"
        const val WORK_NAME = "review_deadline"
    }
}