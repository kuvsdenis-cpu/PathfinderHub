package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "station_visits",
    indices = [Index("stationId"), Index("userId"), Index("clubId"), Index("scannedAt")]
)
data class StationVisitEntity(
    @PrimaryKey val id: String,
    val stationId: String,
    val stationName: String,
    val stationType: String,
    val userId: String,
    val clubId: String,
    val scannedAt: Date,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val accuracyMeters: Float? = null,
    val distanceMeters: Double? = null,
    val geoVerified: Boolean,
    val rewardPoints: Int,
    val pendingSync: Boolean = false,
)
