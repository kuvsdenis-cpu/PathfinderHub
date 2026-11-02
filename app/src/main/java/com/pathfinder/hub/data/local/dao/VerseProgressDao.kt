package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VerseProgressDao {

    @Query("SELECT * FROM verse_progress WHERE status = 'submitted'")
    fun observePending(): Flow<List<VerseProgressEntity>>

    @Query("SELECT * FROM verse_progress WHERE status IN ('approved', 'rejected')")
    fun observeReviewed(): Flow<List<VerseProgressEntity>>

    @Query("SELECT * FROM verse_progress WHERE userId = :userId")
    fun observeByUser(userId: String): Flow<List<VerseProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: VerseProgressEntity)

    @Query("UPDATE verse_progress SET status = :status, approvedBy = :by, approvedAt = :at WHERE userId = :userId AND verseId = :verseId")
    suspend fun updateStatus(userId: String, verseId: String, status: String, by: String?, at: Long?)
}
