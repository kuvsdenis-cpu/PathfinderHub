package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.bible.PearlScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PearlScheduleDao {
    @Query("SELECT * FROM pearl_schedules ORDER BY year DESC")
    fun observeAll(): Flow<List<PearlScheduleEntity>>

    @Query("SELECT * FROM pearl_schedules WHERE year = :year LIMIT 1")
    suspend fun getByYear(year: Int): PearlScheduleEntity?

    @Query("SELECT * FROM pearl_schedules WHERE isActive = 1 ORDER BY year DESC LIMIT 1")
    fun observeActive(): Flow<PearlScheduleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: PearlScheduleEntity)

    @Query("DELETE FROM pearl_schedules WHERE id = :id")
    suspend fun delete(id: String)
}
