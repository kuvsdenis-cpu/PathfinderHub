package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * QR-станция — точка, которую может отсканировать teen.
 *
 * Типы: "entry" | "quest" | "custom".
 */
@Entity(
    tableName = "qr_stations",
    indices = [Index("clubId"), Index("type"), Index("eventId")]
)
data class QrStationEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val eventId: String? = null,
    val name: String,
    val description: String = "",
    val type: String,
    val rewardPoints: Int = 0,
    val rewardAchievementId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 100,
    val status: String = "active",
    val createdBy: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false,
)
