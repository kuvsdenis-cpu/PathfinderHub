package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.ClubDao
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.ConferenceEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ClubRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: ClubDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeClub(clubId: String): Flow<ClubEntity?> = dao.observeClub(clubId)
    suspend fun getClub(clubId: String): ClubEntity? = dao.getClub(clubId)
    suspend fun getClubs(ids: List<String>): List<ClubEntity> =
        if (ids.isEmpty()) emptyList() else dao.getClubs(ids)
    fun observeClubsByConference(conferenceId: String): Flow<List<ClubEntity>> = dao.observeClubsByConference(conferenceId)
    fun observeAllClubs(): Flow<List<ClubEntity>> = dao.observeAllClubs()

    suspend fun upsert(club: ClubEntity) {
        db.withTransaction {
            dao.upsert(club)
            syncQueueManager.enqueue("club", club.id, "CREATE", club)
        }
    }

    suspend fun upsertAll(clubs: List<ClubEntity>) {
        db.withTransaction {
            dao.upsertAll(clubs)
            clubs.forEach { syncQueueManager.enqueue("club", it.id, "CREATE", it) }
        }
    }

    suspend fun update(club: ClubEntity) {
        db.withTransaction {
            dao.update(club)
            syncQueueManager.enqueue("club", club.id, "UPDATE", club)
        }
    }

    suspend fun delete(club: ClubEntity) {
        db.withTransaction {
            dao.delete(club)
            syncQueueManager.enqueue("club", club.id, "DELETE", club)
        }
    }

    // ---------- Soft delete (v32) ----------

    suspend fun softDelete(clubId: String, deletedBy: String, retentionDays: Int = 90) {
        val club = dao.getClub(clubId) ?: return
        val now = java.util.Date()
        val purgeAt = java.util.Date(now.time + retentionDays * 24L * 3600L * 1000L)
        val updated = club.copy(
            deletedAt = now,
            deletedBy = deletedBy,
            purgeAt = purgeAt,
            updatedAt = now,
            updatedBy = deletedBy,
        )
        db.withTransaction {
            dao.update(updated)
            syncQueueManager.enqueue("club", club.id, "UPDATE", updated)
        }
    }

    suspend fun restore(clubId: String, restoredBy: String) {
        val club = dao.getClub(clubId) ?: return
        val now = java.util.Date()
        val updated = club.copy(
            deletedAt = null,
            deletedBy = null,
            purgeAt = null,
            updatedAt = now,
            updatedBy = restoredBy,
        )
        db.withTransaction {
            dao.update(updated)
            syncQueueManager.enqueue("club", club.id, "UPDATE", updated)
        }
    }

    fun observeDeletedClubs(): Flow<List<ClubEntity>> = dao.observeDeletedClubs()

    suspend fun purgeExpired(nowMillis: Long): Int {
        val expired = dao.getExpiredClubs(nowMillis)
        if (expired.isEmpty()) return 0
        db.withTransaction {
            expired.forEach { club ->
                dao.delete(club)
                syncQueueManager.enqueue("club", club.id, "DELETE", club)
            }
        }
        return expired.size
    }

    suspend fun getConference(id: String): ConferenceEntity? = dao.getConference(id)
    fun observeConference(): Flow<ConferenceEntity?> = dao.observeConference()
    suspend fun upsertConference(conference: ConferenceEntity) {
        db.withTransaction { dao.upsertConference(conference) }
    }
}