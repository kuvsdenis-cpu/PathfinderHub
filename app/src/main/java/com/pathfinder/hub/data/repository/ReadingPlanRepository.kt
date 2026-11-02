package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ReadingPlanDao
import com.pathfinder.hub.data.local.dao.ReadingPlanScheduleDao
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanDayEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanProgressEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanScheduleEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingPlanRepository @Inject constructor(
    private val scheduleDao: ReadingPlanScheduleDao,
    private val dayDao: ReadingPlanDao,
    private val syncQueue: SyncQueueManager,
) {
    fun observeAllSchedules(): Flow<List<ReadingPlanScheduleEntity>> = scheduleDao.observeAll()
    fun observeScheduleById(id: String): Flow<ReadingPlanScheduleEntity?> = scheduleDao.observeById(id)
    suspend fun getSchedule(year: Int, kind: String): ReadingPlanScheduleEntity? =
        scheduleDao.getByYearAndKind(year, kind)
    suspend fun upsertSchedule(s: ReadingPlanScheduleEntity) = scheduleDao.upsert(s)

    fun observeAllDays(scheduleId: String): Flow<List<ReadingPlanDayEntity>> =
        dayDao.observeAllDays(scheduleId)
    fun observeDayById(id: String): Flow<ReadingPlanDayEntity?> = dayDao.observeDayById(id)
    fun observeCurrentDay(scheduleId: String, today: Long): Flow<ReadingPlanDayEntity?> =
        dayDao.observeCurrentDay(scheduleId, today)
    suspend fun countDays(scheduleId: String): Int = dayDao.countDays(scheduleId)
    suspend fun upsertDays(days: List<ReadingPlanDayEntity>) = dayDao.upsertDays(days)
    suspend fun deleteDaysForSchedule(scheduleId: String) = dayDao.deleteDaysForSchedule(scheduleId)

    fun observeProgress(userId: String): Flow<List<ReadingPlanProgressEntity>> =
        dayDao.observeProgress(userId)
    fun observeProgressFor(userId: String, dayId: String): Flow<ReadingPlanProgressEntity?> =
        dayDao.observeProgressFor(userId, dayId)

    suspend fun markCompleted(userId: String, dayId: String) {
        val progress = ReadingPlanProgressEntity(
            id = "$userId:$dayId",
            userId = userId,
            dayId = dayId,
            completedAt = System.currentTimeMillis(),
            pendingSync = true,
        )
        dayDao.upsertProgress(progress)
        syncQueue.enqueue(
            entityType = "reading_plan_progress",
            entityId = dayId,
            operation = "upsert",
            payload = progress,
        )
    }

    suspend fun unmarkCompleted(userId: String, dayId: String) {
        val existing = dayDao.getProgress(userId, dayId)
        dayDao.deleteProgress(userId, dayId)
        if (existing != null) {
            syncQueue.enqueue(
                entityType = "reading_plan_progress",
                entityId = dayId,
                operation = "delete",
                payload = existing,
            )
        }
    }
}
