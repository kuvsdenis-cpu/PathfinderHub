package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.SyncConflictDao
import com.pathfinder.hub.data.local.entity.offline.SyncConflictEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncConflictRepository @Inject constructor(
    private val dao: SyncConflictDao,
    private val applier: SyncConflictApplier,
) {

    fun observeUnresolved(userId: String): Flow<List<SyncConflictEntity>> =
        dao.observeUnresolved(userId)

    fun observeUnresolvedCount(userId: String): Flow<Int> =
        dao.observeUnresolvedCount(userId)

    suspend fun getById(id: String): SyncConflictEntity? = dao.getById(id)

    suspend fun upsert(conflict: SyncConflictEntity) = dao.upsert(conflict)

    /**
     * Отметить конфликт как разрешённый и применить выбранное значение к сущности.
     * @param chooseLocal true — взять локальное, false — серверное.
     */
    suspend fun resolve(id: String, resolvedBy: String, chooseLocal: Boolean) {
        val conflict = dao.getById(id) ?: return
        dao.updateResolution(
            id = id,
            status = "resolved",
            resolvedBy = resolvedBy,
            resolvedAt = Date(),
            resolution = if (chooseLocal) "local" else "server",
        )
        applier.apply(conflict, chooseLocal)
    }

    suspend fun deleteById(id: String) = dao.deleteById(id)
}
