package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.info.InfoArticleEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface InfoArticleDao {
    @Query("SELECT * FROM info_articles WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InfoArticleEntity>>

    @Query("SELECT * FROM info_articles WHERE id = :id")
    suspend fun getById(id: String): InfoArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: InfoArticleEntity)

    @Query("UPDATE info_articles SET deletedAt = :date, pendingSync = 1 WHERE id = :id")
    suspend fun softDelete(id: String, date: Date)

    @Query("SELECT * FROM info_articles WHERE pendingSync = 1")
    suspend fun pendingSync(): List<InfoArticleEntity>
}
