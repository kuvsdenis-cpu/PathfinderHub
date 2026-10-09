package com.pathfinder.hub.data.remote

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
