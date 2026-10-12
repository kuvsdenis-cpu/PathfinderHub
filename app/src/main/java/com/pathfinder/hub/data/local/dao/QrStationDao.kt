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

    @Query("""SELECT * FROM qr_stations WHERE clubId = :clubId
        AND status = 'active' AND archivedAt IS NULL
        ORDER BY name""")
    fun observeActiveByClub(clubId: String): Flow<List<QrStationEntity>>

    @Query("""SELECT * FROM qr_stations WHERE clubId = :clubId
        AND archivedAt IS NULL
        ORDER BY name""")
    fun observeAllByClub(clubId: String): Flow<List<QrStationEntity>>

    // ---------- Архивация ----------
    @Query("""SELECT * FROM qr_stations WHERE clubId = :clubId
        AND archivedAt IS NOT NULL
        ORDER BY archivedAt DESC""")
    fun observeArchivedByClub(clubId: String): Flow<List<QrStationEntity>>

    @Query("""SELECT COUNT(*) FROM qr_quest_stations
        WHERE stationId = :stationId""")
    suspend fun countQuestsForStation(stationId: String): Int

    @Query("UPDATE qr_stations SET archivedAt = :at, purgeAfter = :purgeAfter WHERE id = :id")
    suspend fun archiveStation(id: String, at: Long, purgeAfter: Long)

    @Query("UPDATE qr_stations SET archivedAt = NULL, purgeAfter = NULL WHERE id = :id")
    suspend fun restoreStation(id: String)

    @Query("DELETE FROM qr_stations WHERE purgeAfter IS NOT NULL AND purgeAfter < :now")
    suspend fun purgeExpiredStations(now: Long): Int

    /**
     * Станции, НЕ входящие ни в один квест.
     * Используется на вкладке «Одиночные».
     */
    @Query("""
        SELECT * FROM qr_stations
        WHERE clubId = :clubId
          AND id NOT IN (SELECT DISTINCT stationId FROM qr_quest_stations)
        ORDER BY name
    """)
    fun observeStandaloneByClub(clubId: String): Flow<List<QrStationEntity>>

    /**
     * Станции, входящие хотя бы в один квест.
     * Используется на вкладке «Квесты».
     */
    @Query("""
        SELECT * FROM qr_stations
        WHERE clubId = :clubId
          AND id IN (SELECT DISTINCT stationId FROM qr_quest_stations)
        ORDER BY name
    """)
    fun observeInQuestsByClub(clubId: String): Flow<List<QrStationEntity>>

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

    /** Баг #5: визиты станций, привязанных к событию (qr_stations.eventId). */
    @Query("""
        SELECT COUNT(*) FROM station_visits sv
        JOIN qr_stations qs ON sv.stationId = qs.id
        WHERE qs.eventId = :eventId
          AND sv.scannedAt BETWEEN :from AND :to
    """)
    suspend fun countVisitsByEvent(eventId: String, from: Long, to: Long): Int
}
