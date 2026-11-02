package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.pathfinder.hub.data.local.dao.PearlDao
import com.pathfinder.hub.data.local.dao.PearlScheduleDao
import com.pathfinder.hub.data.local.entity.bible.PearlEntity
import com.pathfinder.hub.data.local.entity.bible.PearlScheduleEntity
import com.pathfinder.hub.data.pearls.PearlsJsonDoc
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

@HiltWorker
class PearlSeedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pearlDao: PearlDao,
    private val scheduleDao: PearlScheduleDao,
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun doWork(): Result {
        return try {
            val existing = scheduleDao.getByYear(PEARLS_YEAR)
            if (existing != null && existing.version >= PEARLS_VERSION) {
                Log.d(TAG, "Жемчужины $PEARLS_YEAR v$PEARLS_VERSION уже засеяны, skip")
                return Result.success()
            }
            Log.d(TAG, "Засев жемчужин $PEARLS_YEAR...")

            val raw = applicationContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val doc = gson.fromJson(raw, PearlsJsonDoc::class.java)
            if (doc == null || doc.pearls.isNullOrEmpty()) {
                Log.e(TAG, "Пустой или невалидный $ASSET_PATH")
                return Result.failure()
            }

            val scheduleId = doc.year.toString()
            scheduleDao.upsert(
                PearlScheduleEntity(
                    id = scheduleId,
                    year = doc.year,
                    title = doc.title ?: "Жемчужины ${doc.year}",
                    version = doc.version ?: 1,
                    source = "bundled",
                    importedAt = System.currentTimeMillis(),
                    importedBy = null,
                    isActive = true,
                )
            )

            val seasonTitles = doc.seasons.associate { it.number to (it.title ?: "") }
            val pearls = doc.pearls.mapNotNull { p ->
                val week = p.week ?: return@mapNotNull null
                val season = doc.seasons.firstOrNull {
                    week >= (it.weeksFrom ?: 0) && week <= (it.weeksTo ?: 0)
                }?.number ?: 0
                PearlEntity(
                    id = "$scheduleId:$week",
                    scheduleId = scheduleId,
                    weekNumber = week,
                    season = season,
                    seasonTitle = seasonTitles[season] ?: "",
                    verseId = p.verseId,
                    reference = p.reference ?: "",
                    text = p.text,
                    startDate = isoFmt.parse(p.start ?: "2026-01-01")?.time
                        ?: System.currentTimeMillis(),
                    endDate = isoFmt.parse(p.end ?: "2026-01-07")?.time
                        ?: System.currentTimeMillis(),
                    isExam = p.isExam ?: false,
                )
            }
            pearlDao.upsertPearls(pearls)
            Log.d(TAG, "Жемчужины засеяны: ${pearls.size} шт.")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка засева жемчужин", t)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "PearlSeedWorker"
        const val WORK_NAME = "pearl_seed"
        const val ASSET_PATH = "pearls_2026.json"
        const val PEARLS_YEAR = 2026
        const val PEARLS_VERSION = 1
    }
}
