package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.QrStationDao
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import com.pathfinder.hub.data.remote.FirestoreQrDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QR-станции и посещения.
 *
 * Архитектура:
 *   - Room — источник правды для UI (snapshot-чтение).
 *   - Firestore — для синхронизации между устройствами.
 *   - SyncQueue — fallback, если Firestore упал.
 *
 * Запись:
 *   upsertStation / upsertVisit → Room + Firestore (сразу). Если Firestore
 *   упал → enqueue. SyncWorker потом повторит через FirestoreQrDataSource.
 */
@Singleton
class QrStationRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: QrStationDao,
    private val firestore: FirestoreQrDataSource,
    private val syncQueue: SyncQueueManager,
) {

    // ---------- Чтение ----------

    fun observeActiveStations(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeActiveByClub(clubId)

    fun observeAllStations(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeAllByClub(clubId)

    /** Станции, не входящие ни в один квест. */
    fun observeStandaloneStations(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeStandaloneByClub(clubId)

    /** Станции, входящие хотя бы в один квест. */
    fun observeStationsInQuests(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeInQuestsByClub(clubId)

    suspend fun getStation(id: String): QrStationEntity? = dao.getById(id)

    fun observeVisits(stationId: String, userId: String): Flow<List<StationVisitEntity>> =
        dao.observeVisits(stationId, userId)

    fun observeRecentVisits(userId: String): Flow<List<StationVisitEntity>> =
        dao.observeRecentVisits(userId)

    suspend fun countVisits(userId: String, stationId: String): Int =
        dao.countVisits(userId, stationId)

    suspend fun countVerifiedVisits(userId: String): Int =
        dao.countVerifiedVisits(userId)

    suspend fun countVisitsToday(userId: String, stationId: String): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return dao.countVisitsSince(userId, stationId, cal.timeInMillis)
    }

    /** Баг #5: визиты станций события за период. */
    suspend fun countVisitsByEvent(eventId: String, from: Long, to: Long): Int =
        dao.countVisitsByEvent(eventId, from, to)

    // ---------- Запись ----------

    suspend fun upsertStation(s: QrStationEntity) {
        val remote = firestore.upsertStation(s)
        db.withTransaction {
            dao.upsertStation(s.copy(pendingSync = remote.isFailure))
            if (remote.isFailure) {
                syncQueue.enqueue("qr_station", s.id, "CREATE", s)
            }
        }
    }

    // ---------- Архивация ----------

    fun observeArchivedStations(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeArchivedByClub(clubId)

    /**
     * Архивировать станцию.
     * Правило В: станцию в квестах НЕЛЬЗЯ архивировать —
     * сначала уберите её из квестов.
     */
    suspend fun archiveStation(id: String): Result<Unit> {
        val count = dao.countQuestsForStation(id)
        if (count > 0) {
            return Result.failure(
                IllegalStateException("Станция в $count квестах. Сначала уберите из квестов.")
            )
        }
        return try {
            val now = System.currentTimeMillis()
            val purgeAfter = now + 90L * 24 * 3600 * 1000
            db.withTransaction { dao.archiveStation(id, now, purgeAfter) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreStation(id: String) {
        db.withTransaction { dao.restoreStation(id) }
    }

    suspend fun purgeExpiredStations(now: Long = System.currentTimeMillis()): Int {
        return db.withTransaction { dao.purgeExpiredStations(now) }
    }

    suspend fun deleteStation(id: String) {
        val station = dao.getById(id)
        db.withTransaction {
            dao.deleteStation(id)
            if (station != null) {
                // В Firestore удаляем без ожидания через очередь
                syncQueue.enqueue("qr_station", id, "DELETE", station)
            }
        }
    }

    suspend fun upsertVisit(v: StationVisitEntity) {
        val remote = firestore.upsertVisit(v)
        db.withTransaction {
            dao.upsertVisit(v.copy(pendingSync = remote.isFailure))
            if (remote.isFailure) {
                syncQueue.enqueue("station_visit", v.id, "CREATE", v)
            }
        }
    }

    // ---------- Гео-проверка ----------

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return EARTH_RADIUS_M * c
        }

        fun isWithinRadius(
            stationLat: Double, stationLon: Double, radiusMeters: Int,
            userLat: Double, userLon: Double, accuracyMeters: Float?,
        ): Boolean {
            val d = distanceMeters(stationLat, stationLon, userLat, userLon)
            val effectiveRadius = radiusMeters + (accuracyMeters ?: 0f).toDouble()
            return d <= effectiveRadius
        }
    }
}
