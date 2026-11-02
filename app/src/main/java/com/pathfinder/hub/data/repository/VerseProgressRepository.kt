package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.VerseProgressDao
import com.pathfinder.hub.data.local.entity.learning.VerseProgressEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerseProgressRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: VerseProgressDao,
    private val syncQueueManager: SyncQueueManager,
) {
    fun observePending(): Flow<List<VerseProgressEntity>> = dao.observePending()
    fun observeReviewed(): Flow<List<VerseProgressEntity>> = dao.observeReviewed()
    fun observeByUser(userId: String): Flow<List<VerseProgressEntity>> = dao.observeByUser(userId)

    suspend fun upsert(p: VerseProgressEntity) {
        db.withTransaction {
            dao.upsert(p)
            syncQueueManager.enqueue("verse_progress", "${p.userId}_${p.verseId}", "UPDATE", p)
        }
    }

    suspend fun approve(p: VerseProgressEntity, by: String) {
        db.withTransaction {
            dao.updateStatus(p.userId, p.verseId, "approved", by, Date().time)
            syncQueueManager.enqueue(
                "verse_progress",
                "${p.userId}_${p.verseId}",
                "UPDATE",
                p.copy(status = "approved", approvedBy = by),
            )
        }
    }

    suspend fun reject(p: VerseProgressEntity, by: String) {
        db.withTransaction {
            dao.updateStatus(p.userId, p.verseId, "rejected", by, Date().time)
            syncQueueManager.enqueue(
                "verse_progress",
                "${p.userId}_${p.verseId}",
                "UPDATE",
                p.copy(status = "rejected", approvedBy = by),
            )
        }
    }
}
