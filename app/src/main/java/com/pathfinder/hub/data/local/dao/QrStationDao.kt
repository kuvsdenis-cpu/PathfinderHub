package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrStationDao {

    @Query("SELECT * FROM qr_stations WHERE clubId = :clubId AND status = 'active' ORDER BY name")
    fun observeActiveByClub(clubId: String): Flow<List<QrStationEntity>>

    @Query("SELECT * FROM qr_stations WHERE clubId = :clubId ORDER BY name")
    fun observeAllByClub(clubId: String): Flow<List<QrStationEntity>>

    @Query("SELECT * FROM qr_stations WHERE id = :id")
    suspend fun getById(id: String): QrStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStation(s: QrStationEntity)

    @Query("DELETE FROM qr_stations WHERE id = :id")
    suspend fun deleteStation(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisit(v: StationVisitEntity)

    @Query("SELECT * FROM station_visits WHERE stationId = :stationId AND userId = :userId ORDER BY scannedAt DESC")
    fun observeVisits(stationId: String, userId: String): Flow<List<StationVisitEntity>>

    @Query("SELECT * FROM station_visits WHERE userId = :userId ORDER BY scannedAt DESC LIMIT :limit")
    fun observeRecentVisits(userId: String, limit: Int = 50): Flow<List<StationVisitEntity>>

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND stationId = :stationId")
    suspend fun countVisits(userId: String, stationId: String): Int

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND geoVerified = 1")
    suspend fun countVerifiedVisits(userId: String): Int

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND stationId = :stationId AND scannedAt >= :sinceMs")
    suspend fun countVisitsSince(userId: String, stationId: String, sinceMs: Long): Int
}
