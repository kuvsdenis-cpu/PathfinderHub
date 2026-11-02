package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.ConferenceEventDao
import com.pathfinder.hub.data.local.entity.conference.ConferenceEventEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConferenceEventRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: ConferenceEventDao,
    private val syncQueue: SyncQueueManager,
) {

    fun observePublished(confId: String): Flow<List<ConferenceEventEntity>> =
        dao.observePublishedByConference(confId)

    fun observeAll(confId: String): Flow<List<ConferenceEventEntity>> =
        dao.observeAllByConference(confId)

    suspend fun getById(id: String) = dao.getById(id)

    suspend fun upsert(e: ConferenceEventEntity) {
        db.withTransaction {
            dao.upsert(e)
            // В очередь синка — Firestore rules для conferences/{confId}/events
            syncQueue.enqueue("conference_event", e.id, "CREATE", e)
        }
    }

    suspend fun delete(id: String) {
        db.withTransaction { dao.deleteById(id) }
    }
}
