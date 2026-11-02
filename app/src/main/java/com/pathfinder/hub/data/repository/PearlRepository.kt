package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.PearlDao
import com.pathfinder.hub.data.local.dao.PearlScheduleDao
import com.pathfinder.hub.data.local.entity.bible.PearlEntity
import com.pathfinder.hub.data.local.entity.bible.PearlProgressEntity
import com.pathfinder.hub.data.local.entity.bible.PearlScheduleEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PearlRepository @Inject constructor(
    private val pearlDao: PearlDao,
    private val scheduleDao: PearlScheduleDao,
    private val syncQueue: SyncQueueManager,
) {
    fun observeAllSchedules(): Flow<List<PearlScheduleEntity>> = scheduleDao.observeAll()
    fun observeActiveSchedule(): Flow<PearlScheduleEntity?> = scheduleDao.observeActive()
    suspend fun getScheduleByYear(year: Int): PearlScheduleEntity? = scheduleDao.getByYear(year)
    suspend fun upsertSchedule(s: PearlScheduleEntity) = scheduleDao.upsert(s)

    fun observeAllPearls(scheduleId: String): Flow<List<PearlEntity>> = pearlDao.observeAllPearls(scheduleId)
    fun observePearlById(id: String): Flow<PearlEntity?> = pearlDao.observePearlById(id)
    fun observeCurrentPearl(scheduleId: String, today: Long): Flow<PearlEntity?> = pearlDao.observeCurrentPearl(scheduleId, today)
    suspend fun countPearls(scheduleId: String): Int = pearlDao.countPearls(scheduleId)
    suspend fun upsertPearls(pearls: List<PearlEntity>) = pearlDao.upsertPearls(pearls)
    suspend fun deletePearlsForSchedule(scheduleId: String) = pearlDao.deletePearlsForSchedule(scheduleId)

    fun observeProgress(userId: String): Flow<List<PearlProgressEntity>> = pearlDao.observeProgress(userId)
    fun observeProgressFor(userId: String, pearlId: String): Flow<PearlProgressEntity?> = pearlDao.observeProgressFor(userId, pearlId)

    suspend fun markMemorized(userId: String, pearlId: String) {
        val now = System.currentTimeMillis()
        val existing = pearlDao.getProgress(userId, pearlId)
        val updated = (existing ?: PearlProgressEntity(
            id = "$userId:$pearlId",
            userId = userId,
            pearlId = pearlId,
        )).copy(
            memorized = true,
            memorizedAt = existing?.memorizedAt ?: now,
            lastReviewedAt = now,
            reviewCount = (existing?.reviewCount ?: 0) + 1,
            pendingSync = true,
        )
        pearlDao.upsertProgress(updated)
        syncQueue.enqueue(
            entityType = "pearl_progress",
            entityId = pearlId,
            operation = "upsert",
            payload = updated,
        )
    }

    suspend fun unmarkMemorized(userId: String, pearlId: String) {
        val existing = pearlDao.getProgress(userId, pearlId) ?: return
        val updated = existing.copy(
            memorized = false,
            memorizedAt = null,
            lastReviewedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        pearlDao.upsertProgress(updated)
        syncQueue.enqueue(
            entityType = "pearl_progress",
            entityId = pearlId,
            operation = "upsert",
            payload = updated,
        )
    }

    suspend fun saveTrainingResult(userId: String, pearlId: String, score: Int) {
        val now = System.currentTimeMillis()
        val existing = pearlDao.getProgress(userId, pearlId)
        val base = existing ?: PearlProgressEntity(
            id = "$userId:$pearlId",
            userId = userId,
            pearlId = pearlId,
        )
        val updated = base.copy(
            trainingScore = maxOf(base.trainingScore ?: 0, score.coerceIn(0, 100)),
            trainingAttempts = base.trainingAttempts + 1,
            lastTrainedAt = now,
            pendingSync = true,
        )
        pearlDao.upsertProgress(updated)
        syncQueue.enqueue(
            entityType = "pearl_progress",
            entityId = pearlId,
            operation = "upsert",
            payload = updated,
        )
    }
}
