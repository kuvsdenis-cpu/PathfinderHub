package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanDayEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPlanDao {

    @Query("SELECT * FROM reading_plan_days WHERE scheduleId = :scheduleId ORDER BY dayNumber")
    fun observeAllDays(scheduleId: String): Flow<List<ReadingPlanDayEntity>>

    @Query("SELECT * FROM reading_plan_days WHERE id = :id LIMIT 1")
    fun observeDayById(id: String): Flow<ReadingPlanDayEntity?>

    @Query("""
        SELECT * FROM reading_plan_days
        WHERE scheduleId = :scheduleId AND date <= :today
        ORDER BY date DESC LIMIT 1
    """)
    fun observeCurrentDay(scheduleId: String, today: Long): Flow<ReadingPlanDayEntity?>

    @Query("SELECT COUNT(*) FROM reading_plan_days WHERE scheduleId = :scheduleId")
    suspend fun countDays(scheduleId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDays(days: List<ReadingPlanDayEntity>)

    @Query("DELETE FROM reading_plan_days WHERE scheduleId = :scheduleId")
    suspend fun deleteDaysForSchedule(scheduleId: String)

    @Query("SELECT * FROM reading_plan_progress WHERE userId = :userId")
    fun observeProgress(userId: String): Flow<List<ReadingPlanProgressEntity>>

    @Query("SELECT * FROM reading_plan_progress WHERE userId = :userId AND dayId = :dayId LIMIT 1")
    fun observeProgressFor(userId: String, dayId: String): Flow<ReadingPlanProgressEntity?>

    @Query("SELECT * FROM reading_plan_progress WHERE userId = :userId AND dayId = :dayId LIMIT 1")
    suspend fun getProgress(userId: String, dayId: String): ReadingPlanProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: ReadingPlanProgressEntity)

    @Query("DELETE FROM reading_plan_progress WHERE userId = :userId AND dayId = :dayId")
    suspend fun deleteProgress(userId: String, dayId: String)
}
