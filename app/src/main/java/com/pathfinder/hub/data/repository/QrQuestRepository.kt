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

    // ---------- Архивация ----------

    fun observeArchivedQuests(clubId: String): Flow<List<QrQuestEntity>> =
        dao.observeArchivedByClub(clubId)

    /** Архивировать квест. Связи со станциями НЕ трогаем (см. правило В). */
    suspend fun archiveQuest(id: String) {
        val now = System.currentTimeMillis()
        val purgeAfter = now + 90L * 24 * 3600 * 1000
        db.withTransaction { dao.archiveQuest(id, now, purgeAfter) }
    }

    suspend fun restoreQuest(id: String) {
        db.withTransaction { dao.restoreQuest(id) }
    }

    suspend fun purgeExpiredQuests(now: Long = System.currentTimeMillis()): Int {
        return db.withTransaction { dao.purgeExpiredQuests(now) }
    }

    // ---------- Связи ----------

    fun observeQuestStations(questId: String): Flow<List<QrQuestStationEntity>> =
        dao.observeQuestStations(questId)

    /** Все связи квест↔станция для клуба. */
    fun observeAllLinksForClub(clubId: String): Flow<List<QrQuestStationEntity>> =
        dao.observeAllLinksForClub(clubId)

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

    /**
     * Прогресс по всем квестам клуба за период.
     * Используется в отчёте.
     */
    suspend fun getProgressInPeriod(clubId: String, from: Long, to: Long): List<QrQuestProgressEntity> =
        dao.getProgressInPeriod(clubId, from, to)

    suspend fun upsertProgress(p: QrQuestProgressEntity) {
        db.withTransaction { dao.upsertProgress(p) }
    }
}
