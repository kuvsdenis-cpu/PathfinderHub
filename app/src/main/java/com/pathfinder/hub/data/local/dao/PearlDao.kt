package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.bible.PearlEntity
import com.pathfinder.hub.data.local.entity.bible.PearlProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PearlDao {
    @Query("SELECT * FROM pearls WHERE scheduleId = :scheduleId ORDER BY weekNumber")
    fun observeAllPearls(scheduleId: String): Flow<List<PearlEntity>>

    @Query("SELECT * FROM pearls WHERE id = :id")
    fun observePearlById(id: String): Flow<PearlEntity?>

    @Query("SELECT * FROM pearls WHERE scheduleId = :scheduleId AND startDate <= :today AND endDate >= :today LIMIT 1")
    fun observeCurrentPearl(scheduleId: String, today: Long): Flow<PearlEntity?>

    @Query("SELECT COUNT(*) FROM pearls WHERE scheduleId = :scheduleId")
    suspend fun countPearls(scheduleId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPearls(pearls: List<PearlEntity>)

    @Query("DELETE FROM pearls WHERE scheduleId = :scheduleId")
    suspend fun deletePearlsForSchedule(scheduleId: String)

    @Query("SELECT * FROM pearl_progress WHERE userId = :userId")
    fun observeProgress(userId: String): Flow<List<PearlProgressEntity>>

    @Query("SELECT * FROM pearl_progress WHERE userId = :userId AND pearlId = :pearlId LIMIT 1")
    fun observeProgressFor(userId: String, pearlId: String): Flow<PearlProgressEntity?>

    @Query("SELECT * FROM pearl_progress WHERE userId = :userId AND pearlId = :pearlId LIMIT 1")
    suspend fun getProgress(userId: String, pearlId: String): PearlProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: PearlProgressEntity)

    @Query("DELETE FROM pearl_progress WHERE userId = :userId AND pearlId = :pearlId")
    suspend fun deleteProgress(userId: String, pearlId: String)

    @Query("""
        UPDATE pearl_progress
        SET trainingScore = CASE
                WHEN trainingScore IS NULL OR :score > trainingScore THEN :score
                ELSE trainingScore
            END,
            trainingAttempts = trainingAttempts + 1,
            lastTrainedAt = :trainedAt,
            pendingSync = 1
        WHERE userId = :userId AND pearlId = :pearlId
    """)
    suspend fun updateTrainingResult(
        userId: String,
        pearlId: String,
        score: Int,
        trainedAt: Long,
    )
}
