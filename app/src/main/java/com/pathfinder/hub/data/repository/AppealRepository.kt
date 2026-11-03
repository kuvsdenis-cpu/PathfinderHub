package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.local.entity.planning.AppealEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppealRepository @Inject constructor(
    private val dao: ModerationDao,
    private val syncQueueManager: SyncQueueManager,
) {

    fun observePendingAppeals(): Flow<List<AppealEntity>> =
        dao.observePendingAppeals()

    fun observePendingAppealsByClub(clubId: String): Flow<List<AppealEntity>> =
        dao.observePendingAppealsByClub(clubId)

    /**
     * Разрешить апелляцию.
     * @param status "approved" — принять, "rejected" — отклонить.
     */
    suspend fun resolve(appealId: String, status: String, by: String) {
        val appeal = dao.getAppealById(appealId) ?: return
        val request = dao.getRequestById(appeal.moderationRequestId)
        val clubId = request?.clubId ?: return
        val now = System.currentTimeMillis()
        dao.resolveAppeal(appealId, status, by, now)
        syncQueueManager.enqueue(
            entityType = "appeal",
            entityId = appealId,
            operation = "UPDATE",
            payload = mapOf(
                "id" to appealId,
                "moderationRequestId" to appeal.moderationRequestId,
                "authorId" to appeal.authorId,
                "text" to appeal.text,
                "status" to status,
                "reviewedBy" to by,
                "reviewedAt" to now,
                "createdAt" to appeal.createdAt.time,
                "clubId" to clubId,
            )
        )
    }
}
