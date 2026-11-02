package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.ParentChildLinkDao
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentChildLinkRepository @Inject constructor(
    private val dao: ParentChildLinkDao
) {
    fun observeByParent(parentId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeByParent(parentId)

    fun observeByChild(childId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeByChild(childId)

    suspend fun upsert(link: ParentChildLinkEntity) = dao.upsert(link)

    suspend fun getByParentAndChild(parentId: String, childId: String): ParentChildLinkEntity? =
        dao.getByParentAndChild(parentId, childId)

    fun observeLinksByClub(clubId: String): Flow<List<ParentChildLinkEntity>> =
        dao.observeByClub(clubId)

    suspend fun link(
        parentId: String,
        childId: String,
        clubId: String,
        linkedBy: String,
    ) {
        val existing = dao.getByParentAndChild(parentId, childId)
        val link = ParentChildLinkEntity(
            parentId = parentId,
            childId = childId,
            clubId = clubId,
            relationship = existing?.relationship ?: "parent",
            status = "active",
            linkedBy = linkedBy,
            linkedAt = java.util.Date(),
            revokedAt = null,
            revokedReason = null,
            inviteCode = existing?.inviteCode,
        )
        dao.upsert(link)
    }

    suspend fun revoke(parentId: String, childId: String, reason: String?) {
        val existing = dao.getByParentAndChild(parentId, childId) ?: return
        dao.upsert(
            existing.copy(
                revokedAt = java.util.Date(),
                revokedReason = reason,
            )
        )
    }
}
