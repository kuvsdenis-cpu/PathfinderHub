package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY title")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE (clubId IS NULL OR clubId = :clubId) AND deletedAt IS NULL ORDER BY title")
    fun observeAvailable(clubId: String?): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BookEntity>)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM books WHERE clubId IS NULL AND deletedAt IS NULL")
    suspend fun countGlobal(): Int
}
