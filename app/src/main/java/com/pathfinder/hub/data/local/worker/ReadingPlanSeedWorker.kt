package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.pathfinder.hub.data.local.dao.ReadingPlanDao
import com.pathfinder.hub.data.local.dao.ReadingPlanScheduleDao
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanDayEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanScheduleEntity
import com.pathfinder.hub.data.readingplan.ReadingPlanJsonDoc
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

@HiltWorker
class ReadingPlanSeedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduleDao: ReadingPlanScheduleDao,
    private val dayDao: ReadingPlanDao,
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun doWork(): Result {
        return try {
            var ok = true
            ok = ok && seedOne(ASSET_BIBLE, "BIBLE")
            ok = ok && seedOne(ASSET_EGW, "EGW")
            if (ok) Result.success() else Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка засева плана чтения", t)
            Result.retry()
        }
    }

    private suspend fun seedOne(assetPath: String, kind: String): Boolean {
        return try {
            val raw = applicationContext.assets.open(assetPath)
                .bufferedReader().use { it.readText() }
            val doc = gson.fromJson(raw, ReadingPlanJsonDoc::class.java)
            if (doc == null || doc.days.isEmpty()) {
                Log.e(TAG, "Пустой или невалидный $assetPath")
                return false
            }

            val scheduleId = "${kind.lowercase()}-${doc.year}"
            val existing = scheduleDao.getByYearAndKind(doc.year, kind)
            if (existing != null && existing.version >= (doc.version ?: 1)) {
                Log.d(TAG, "План $scheduleId уже засеян, skip")
                return true
            }

            scheduleDao.upsert(
                ReadingPlanScheduleEntity(
                    id = scheduleId,
                    year = doc.year,
                    title = doc.title ?: "План $kind ${doc.year}",
                    kind = kind,
                    version = doc.version ?: 1,
                    source = "bundled",
                    importedAt = System.currentTimeMillis(),
                    importedBy = null,
                    isActive = true,
                )
            )

            val days = doc.days.mapNotNull { d ->
                val dateStr = d.date ?: return@mapNotNull null
                ReadingPlanDayEntity(
                    id = "$scheduleId:${d.dayNumber}",
                    scheduleId = scheduleId,
                    dayNumber = d.dayNumber,
                    date = isoFmt.parse(dateStr)?.time ?: System.currentTimeMillis(),
                    reference = d.reference ?: "",
                    bookId = d.bookId,
                    chapterFrom = d.chapterFrom,
                    chapterTo = d.chapterTo,
                    passageText = d.passageText,
                )
            }
            dayDao.deleteDaysForSchedule(scheduleId)
            dayDao.upsertDays(days)
            Log.d(TAG, "Засеян $scheduleId: ${days.size} дней")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка засева $assetPath", t)
            false
        }
    }

    companion object {
        const val TAG = "ReadingPlanSeedWorker"
        const val WORK_NAME = "reading_plan_seed"
        const val ASSET_BIBLE = "reading_plan_bible_2026.json"
        const val ASSET_EGW = "reading_plan_egw_2026.json"
    }
}
