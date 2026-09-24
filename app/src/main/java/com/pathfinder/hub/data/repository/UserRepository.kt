package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.UserDao
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val dao: UserDao,
    private val syncQueueManager: SyncQueueManager
) {
    fun observeUser(id: String): Flow<UserEntity?> = dao.observeUser(id)
    suspend fun getUser(id: String): UserEntity? = dao.getUser(id)
    suspend fun getUserByEmail(email: String): UserEntity? = dao.getUserByEmail(email)
    fun observeClubMembers(clubId: String): Flow<List<UserEntity>> = dao.observeClubMembers(clubId)
    fun observeClubMembersByRole(c: String, r: String): Flow<List<UserEntity>> = dao.observeClubMembersByRole(c, r)

    suspend fun upsert(u: UserEntity) {
        dao.upsert(u)
        syncQueueManager.enqueue("user", u.id, "CREATE", u)
    }

    suspend fun upsertAll(us: List<UserEntity>) {
        dao.upsertAll(us)
        us.forEach { syncQueueManager.enqueue("user", it.id, "CREATE", it) }
    }

    suspend fun update(u: UserEntity) {
        dao.update(u)
        syncQueueManager.enqueue("user", u.id, "UPDATE", u)
    }

    suspend fun delete(u: UserEntity) {
        dao.delete(u)
        syncQueueManager.enqueue("user", u.id, "DELETE", u)
    }

    suspend fun updateAccessLevel(id: String, l: String) {
        dao.updateAccessLevel(id, l)
        dao.getUser(id)?.let { syncQueueManager.enqueue("user", id, "UPDATE", it) }
    }

    suspend fun completeOnboarding(id: String) {
        dao.completeOnboarding(id)
        dao.getUser(id)?.let { syncQueueManager.enqueue("user", id, "UPDATE", it) }
    }

    suspend fun requestDeletion(id: String, s: String, at: Long) {
        dao.requestDeletion(id, s, at)
        dao.getUser(id)?.let { syncQueueManager.enqueue("user", id, "UPDATE", it) }
    }
}