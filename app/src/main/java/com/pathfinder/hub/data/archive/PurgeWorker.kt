package com.pathfinder.hub.data.archive

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.NotificationRepository
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.ReportRepository
import com.pathfinder.hub.data.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Раз в сутки удаляет всё, у чего purgeAfter < now.
 * События + задания + домашки + комментарии + QR-квесты + QR-станции.
 */
@HiltWorker
class PurgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventRepository: EventRepository,
    private val taskRepository: TaskRepository,
    private val achievementRepository: AchievementRepository,
    private val moderationRepository: ModerationRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val qrStationRepository: QrStationRepository,
    private val reportRepository: ReportRepository,
    private val notificationRepository: NotificationRepository,
    private val customGroupRepository: CustomGroupRepository,
    private val clubRepository: ClubRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val ev = eventRepository.purgeExpired(now)
            val ts = taskRepository.purgeExpired(now)
            val hw = achievementRepository.purgeExpiredHomework(now)
            val cm = moderationRepository.purgeExpiredComments(now)
            val qq = qrQuestRepository.purgeExpiredQuests(now)
            val qs = qrStationRepository.purgeExpiredStations(now)
            val rp = reportRepository.purgeExpiredReports(now)
            val cg = customGroupRepository.purgeExpired(now)
            val cl = clubRepository.purgeExpired(now)
            // Уведомления старше 30 дней (не архив — просто удаление)
            notificationRepository.purgeOlderThan(30)
            Log.d(TAG, "Purge done: events=$ev, tasks=$ts, homework=$hw, " +
                    "comments=$cm, quests=$qq, stations=$qs, reports=$rp, " +
                    "groups=$cg, clubs=$cl, notifications=cleaned(>30d)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Purge failed", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "PurgeWorker"
        const val WORK_NAME = "archive_purge"
    }
}
