package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.local.entity.planning.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ModerationRepository @Inject constructor(private val dao: ModerationDao) {
    fun observeApprovedComments(t: String, id: String): Flow<List<CommentEntity>> =
        dao.observeApprovedComments(t, id)
    fun observeUserComments(u: String): Flow<List<CommentEntity>> = dao.observeUserComments(u)
    fun observePendingComments(): Flow<List<CommentEntity>> = dao.observePendingComments()
    suspend fun upsertComment(c: CommentEntity) = dao.upsertComment(c)
    suspend fun moderateComment(id: String, s: String, b: String, at: Long, r: String?) =
        dao.moderateComment(id, s, b, at, r)
    fun observePendingRequests(c: String): Flow<List<ModerationRequestEntity>> =
        dao.observePendingRequests(c)
    suspend fun upsertRequest(r: ModerationRequestEntity) = dao.upsertRequest(r)
    suspend fun resolveRequest(id: String, s: String, b: String, at: Long, r: String?) =
        dao.resolveRequest(id, s, b, at, r)
    suspend fun escalate(id: String, l: Int, t: String, at: Long) = dao.escalate(id, l, t, at)
    fun observePendingAppeals(): Flow<List<AppealEntity>> = dao.observePendingAppeals()
    suspend fun upsertAppeal(a: AppealEntity) = dao.upsertAppeal(a)
    suspend fun resolveAppeal(id: String, s: String, b: String, at: Long) = dao.resolveAppeal(id, s, b, at)
    suspend fun upsertHistory(h: ModerationHistoryEntity) = dao.upsertHistory(h)
}
