package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.BookReportDao
import com.pathfinder.hub.data.local.entity.learning.BookReportEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookReportRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: BookReportDao,
    private val syncQueueManager: SyncQueueManager,
) {
    fun observePending(): Flow<List<BookReportEntity>> = dao.observePending()
    fun observeReviewed(): Flow<List<BookReportEntity>> = dao.observeReviewed()
    fun observeByUser(userId: String): Flow<List<BookReportEntity>> = dao.observeByUser(userId)

    suspend fun getById(id: String): BookReportEntity? = dao.getById(id)

    suspend fun upsert(r: BookReportEntity) {
        db.withTransaction {
            dao.upsert(r)
            syncQueueManager.enqueue("book_report", r.id, "UPDATE", r)
        }
    }

    suspend fun approve(r: BookReportEntity, by: String) {
        db.withTransaction {
            dao.updateStatus(r.id, "approved", by, Date().time)
            syncQueueManager.enqueue("book_report", r.id, "UPDATE", r.copy(status = "approved"))
        }
    }

    suspend fun reject(r: BookReportEntity, by: String) {
        db.withTransaction {
            dao.updateStatus(r.id, "rejected", by, Date().time)
            syncQueueManager.enqueue("book_report", r.id, "UPDATE", r.copy(status = "rejected"))
        }
    }
}
