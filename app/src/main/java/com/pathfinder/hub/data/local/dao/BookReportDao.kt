package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookReportDao {

    @Query("SELECT * FROM book_reports WHERE status = 'submitted' ORDER BY approvedAt DESC")
    fun observePending(): Flow<List<BookReportEntity>>

    @Query("SELECT * FROM book_reports WHERE status IN ('approved', 'rejected') ORDER BY approvedAt DESC")
    fun observeReviewed(): Flow<List<BookReportEntity>>

    @Query("SELECT * FROM book_reports WHERE userId = :userId ORDER BY approvedAt DESC")
    fun observeByUser(userId: String): Flow<List<BookReportEntity>>

    @Query("SELECT * FROM book_reports WHERE id = :id")
    suspend fun getById(id: String): BookReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: BookReportEntity)

    @Query("UPDATE book_reports SET status = :status, approvedBy = :by, approvedAt = :at WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, by: String?, at: Long?)
}
