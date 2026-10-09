package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.QrQuestDao
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestStationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QR-квесты: логика чтения/записи.
 *
 * Firestore-синк пока НЕ реализован (нужен resolveCollectionPath
 * для qr_quest / qr_quest_progress в SyncWorker). Всё локально в Room.
 */
@Singleton
class QrQuestRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: QrQuestDao,
) {

    // ---------- Квесты ----------

    fun observeActiveQuests(clubId: String): Flow<List<QrQuestEntity>> =
        dao.observeActiveByClub(clubId)

    fun observeAllQuests(clubId: String): Flow<List<QrQuestEntity>> =
        dao.observeAllByClub(clubId)

    suspend fun getQuest(id: String): QrQuestEntity? = dao.getById(id)

    suspend fun upsertQuest(q: QrQuestEntity) {
        db.withTransaction { dao.upsertQuest(q) }
    }

    suspend fun deleteQuest(id: String) {
        db.withTransaction { dao.deleteQuest(id) }
    }

    // ---------- Связи ----------

    fun observeQuestStations(questId: String): Flow<List<QrQuestStationEntity>> =
        dao.observeQuestStations(questId)

    suspend fun getQuestStations(questId: String): List<QrQuestStationEntity> =
        dao.getQuestStations(questId)

    suspend fun getQuestsForStation(stationId: String): List<QrQuestStationEntity> =
        dao.getQuestsForStation(stationId)

    suspend fun getStepCount(questId: String): Int = dao.getStepCount(questId)

    suspend fun upsertQuestStation(s: QrQuestStationEntity) {
        db.withTransaction { dao.upsertQuestStation(s) }
    }

    suspend fun clearQuestStations(questId: String) {
        db.withTransaction { dao.clearQuestStations(questId) }
    }

    suspend fun deleteQuestStation(questId: String, stationId: String) {
        db.withTransaction { dao.deleteQuestStation(questId, stationId) }
    }

    /**
     * Установить полный список станций квеста.
     * Пересчитывает stepIndex по порядку переданного списка.
     */
    suspend fun setQuestStations(questId: String, stationIds: List<String>) {
        db.withTransaction {
            dao.clearQuestStations(questId)
            stationIds.forEachIndexed { idx, sid ->
                dao.upsertQuestStation(
                    QrQuestStationEntity(
                        questId = questId,
                        stationId = sid,
                        stepIndex = idx + 1,
                    )
                )
            }
        }
    }

    // ---------- Прогресс ----------

    fun observeProgress(questId: String, userId: String): Flow<QrQuestProgressEntity?> =
        dao.observeProgress(questId, userId)

    suspend fun getProgress(questId: String, userId: String): QrQuestProgressEntity? =
        dao.getProgress(questId, userId)

    fun observeAllProgressByUser(userId: String): Flow<List<QrQuestProgressEntity>> =
        dao.observeAllProgressByUser(userId)

    suspend fun upsertProgress(p: QrQuestProgressEntity) {
        db.withTransaction { dao.upsertProgress(p) }
    }
}
