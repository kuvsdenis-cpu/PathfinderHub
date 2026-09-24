package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ClubDao
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.ConferenceEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ClubRepository @Inject constructor(
    private val dao: ClubDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeClub(clubId: String): Flow<ClubEntity?> = dao.observeClub(clubId)
    suspend fun getClub(clubId: String): ClubEntity? = dao.getClub(clubId)
    fun observeClubsByConference(conferenceId: String): Flow<List<ClubEntity>> = dao.observeClubsByConference(conferenceId)
    fun observeAllClubs(): Flow<List<ClubEntity>> = dao.observeAllClubs()

    suspend fun upsert(club: ClubEntity) {
        dao.upsert(club)
        syncQueueManager.enqueue("club", club.id, "CREATE", club)
    }

    suspend fun upsertAll(clubs: List<ClubEntity>) {
        dao.upsertAll(clubs)
        clubs.forEach { syncQueueManager.enqueue("club", it.id, "CREATE", it) }
    }

    suspend fun update(club: ClubEntity) {
        dao.update(club)
        syncQueueManager.enqueue("club", club.id, "UPDATE", club)
    }

    suspend fun delete(club: ClubEntity) {
        dao.delete(club)
        syncQueueManager.enqueue("club", club.id, "DELETE", club)
    }

    suspend fun getConference(id: String): ConferenceEntity? = dao.getConference(id)
    fun observeConference(): Flow<ConferenceEntity?> = dao.observeConference()
    suspend fun upsertConference(conference: ConferenceEntity) = dao.upsertConference(conference)
}