package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPlanScheduleDao {

    @Query("SELECT * FROM reading_plan_schedules ORDER BY year DESC, kind")
    fun observeAll(): Flow<List<ReadingPlanScheduleEntity>>

    @Query("SELECT * FROM reading_plan_schedules WHERE year = :year AND kind = :kind LIMIT 1")
    suspend fun getByYearAndKind(year: Int, kind: String): ReadingPlanScheduleEntity?

    @Query("SELECT * FROM reading_plan_schedules WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ReadingPlanScheduleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: ReadingPlanScheduleEntity)

    @Query("DELETE FROM reading_plan_schedules WHERE id = :id")
    suspend fun delete(id: String)
}
