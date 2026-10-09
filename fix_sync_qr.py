#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Firestore sync for QR stations - 08.10.2026
Запуск:
    python fix_sync_qr.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return

    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)

    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. FirestoreQrDataSource.kt
# ============================================================
DS = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для QR-станций и посещений.
 *
 * Коллекции:
 *   clubs/{clubId}/qrStations/{stationId}
 *   clubs/{clubId}/stationVisits/{visitId}
 *
 * Даты конвертируются в Timestamp (не строки).
 */
@Singleton
class FirestoreQrDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreQrDS"
        const val COL_STATIONS = "qrStations"
        const val COL_VISITS = "stationVisits"
    }

    private fun stationsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COL_STATIONS)

    private fun visitsRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COL_VISITS)

    suspend fun upsertStation(s: QrStationEntity): Result<Unit> = try {
        if (s.clubId.isBlank()) throw IllegalArgumentException("clubId пустой")
        stationsRef(s.clubId).document(s.id).set(mapStation(s)).await()
        Log.d(TAG, "station upsert OK: ${s.clubId}/${s.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "station upsert FAILED: ${s.id}", e)
        Result.failure(e)
    }

    suspend fun deleteStation(clubId: String, stationId: String): Result<Unit> = try {
        stationsRef(clubId).document(stationId).delete().await()
        Log.d(TAG, "station delete OK: $clubId/$stationId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "station delete FAILED: $stationId", e)
        Result.failure(e)
    }

    suspend fun upsertVisit(v: StationVisitEntity): Result<Unit> = try {
        if (v.clubId.isBlank()) throw IllegalArgumentException("clubId пустой")
        visitsRef(v.clubId).document(v.id).set(mapVisit(v)).await()
        Log.d(TAG, "visit upsert OK: ${v.clubId}/${v.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "visit upsert FAILED: ${v.id}", e)
        Result.failure(e)
    }

    private fun mapStation(s: QrStationEntity): Map<String, Any?> = mapOf(
        "id" to s.id,
        "clubId" to s.clubId,
        "eventId" to s.eventId,
        "name" to s.name,
        "description" to s.description,
        "type" to s.type,
        "rewardPoints" to s.rewardPoints,
        "rewardAchievementId" to s.rewardAchievementId,
        "latitude" to s.latitude,
        "longitude" to s.longitude,
        "radiusMeters" to s.radiusMeters,
        "status" to s.status,
        "createdBy" to s.createdBy,
        "createdAt" to Timestamp(s.createdAt),
        "updatedAt" to Timestamp(s.updatedAt),
    )

    private fun mapVisit(v: StationVisitEntity): Map<String, Any?> = mapOf(
        "id" to v.id,
        "stationId" to v.stationId,
        "stationName" to v.stationName,
        "stationType" to v.stationType,
        "userId" to v.userId,
        "clubId" to v.clubId,
        "scannedAt" to Timestamp(v.scannedAt),
        "userLat" to v.userLat,
        "userLon" to v.userLon,
        "accuracyMeters" to v.accuracyMeters,
        "distanceMeters" to v.distanceMeters,
        "geoVerified" to v.geoVerified,
        "rewardPoints" to v.rewardPoints,
    )

    @Suppress("unused")
    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreQrDataSource.kt", DS)


# ============================================================
# 2. QrStationRepository.kt — REWRITE
# ============================================================
REPO = '''package com.pathfinder.hub.data.repository

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
'''
rewrite("app/src/main/java/com/pathfinder/hub/data/repository/QrStationRepository.kt", REPO)


# ============================================================
# 3. RepositoryModule.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/di/RepositoryModule.kt", [
    ("""    @Provides @Singleton fun provideQrStationDao(db: AppDatabase): QrStationDao = db.qrStationDao()
    @Provides @Singleton fun provideQrStationRepo(d: QrStationDao) = QrStationRepository(d)""",
     """    @Provides @Singleton fun provideQrStationDao(db: AppDatabase): QrStationDao = db.qrStationDao()

    @Provides @Singleton
    fun provideQrStationRepo(
        db: AppDatabase,
        d: QrStationDao,
        firestore: com.pathfinder.hub.data.remote.FirestoreQrDataSource,
        syncQueue: SyncQueueManager,
    ) = QrStationRepository(db, d, firestore, syncQueue)"""),
], skip_if_marker="FirestoreQrDataSource")


# ============================================================
# 4. SyncWorker.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/sync/SyncWorker.kt", [
    # импорты
    ("import com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource",
     "import com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource\n"
     "import com.pathfinder.hub.data.remote.FirestoreQrDataSource\n"
     "import com.pathfinder.hub.data.local.dao.QrStationDao\n"
     "import com.pathfinder.hub.data.local.entity.qr.QrStationEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity"),

    # инжект
    ("""    private val contentDataSource: FirestoreRequirementContentDataSource,
    private val answerDataSource: FirestoreTaskAnswerDataSource,
) : CoroutineWorker(context, params) {""",
     """    private val contentDataSource: FirestoreRequirementContentDataSource,
    private val answerDataSource: FirestoreTaskAnswerDataSource,
    private val qrDataSource: FirestoreQrDataSource,
    private val qrDao: QrStationDao,
) : CoroutineWorker(context, params) {"""),

    # processItem — добавить ветки
    ("""            if (item.entityType == "requirement_task_answer") {
                return processRequirementTaskAnswer(item)
            }""",
     """            if (item.entityType == "requirement_task_answer") {
                return processRequirementTaskAnswer(item)
            }
            if (item.entityType == "qr_station") {
                return processQrStation(item)
            }
            if (item.entityType == "station_visit") {
                return processStationVisit(item)
            }"""),

    # resolveCollectionPath — маппинг (fallback, но не должен использоваться)
    ("""            "report" -> if (clubId != null) "clubs/$clubId/reports" else null""",
     """            "report" -> if (clubId != null) "clubs/$clubId/reports" else null
            "qr_station" -> if (clubId != null) "clubs/$clubId/qrStations" else null
            "station_visit" -> if (clubId != null) "clubs/$clubId/stationVisits" else null"""),

    # process методы — перед resolveCollectionPath
    ("""    private fun classifyError(error: Throwable?): ProcessResult {""",
     """    private suspend fun processQrStation(item: SyncQueueItemEntity): ProcessResult {
        val station = try {
            gson.fromJson(item.payload, QrStationEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "station deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (station.clubId.isBlank() || station.id.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = qrDataSource.upsertStation(station)
                if (r.isSuccess) {
                    try { qrDao.upsertStation(station.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = qrDataSource.deleteStation(station.clubId, station.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processStationVisit(item: SyncQueueItemEntity): ProcessResult {
        val visit = try {
            gson.fromJson(item.payload, StationVisitEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "visit deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (visit.clubId.isBlank() || visit.id.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = qrDataSource.upsertVisit(visit)
                if (r.isSuccess) {
                    try { qrDao.upsertVisit(visit.copy(pendingSync = false)) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private fun classifyError(error: Throwable?): ProcessResult {"""),
], skip_if_marker="processQrStation")


# ============================================================
# 5. firestore.rules
# ============================================================
patch("firestore.rules", [
    ("""      match /reports/{reportId} {
        allow read: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow create: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isConferenceAdmin();
      }""",
     """      // ============================================================
      // QR-СТАНЦИИ
      // ============================================================
      match /qrStations/{stationId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isSignedIn() && (isDirector() && isOwnClub(clubId));
      }

      match /stationVisits/{visitId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create: if isSignedIn()
          && request.resource.data.userId == uid()
          && request.resource.data.clubId == clubId;
        allow update, delete: if false;
      }

      match /reports/{reportId} {
        allow read: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow create: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow update: if isSignedIn() && (isClubStaff() && isOwnClub(clubId));
        allow delete: if isConferenceAdmin();
      }"""),
], skip_if_marker='match /qrStations/')


print()
print("=" * 60)
print("Готово.")
print("=" * 60)
print()
print("Дальше:")
print("  1. Сборка:")
print("       .\\gradlew assembleDebug")
print("       .\\gradlew installDebug")
print("  2. Задеплой правила:")
print("       firebase deploy --only firestore:rules")
print("  3. Проверка end-to-end:")
print("       - Директор создал QR-станцию → Firestore: clubs/demo_club/qrStations/{id}")
print("       - Teen сканировал → Firestore: clubs/demo_club/stationVisits/{id}")
print("       - Другое устройство (тот же клуб) видит изменения после sync")
print()
print("Откат: рядом с каждым .bak.<timestamp>")