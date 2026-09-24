package com.pathfinder.hub.data.local.entity.notifications
import androidx.room.*
import java.util.Date

@Entity(tableName = "fcm_tokens")
data class FCMTokenEntity(
    @PrimaryKey val token: String, val userId: String, val deviceId: String,
    val platform: String, val updatedAt: Date
)
