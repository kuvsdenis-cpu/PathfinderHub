package com.pathfinder.hub.data.local.entity.notifications
import androidx.room.*
import java.util.Date

@Entity(tableName = "notification_analytics")
data class NotificationAnalyticsEntity(
    @PrimaryKey val notificationId: String, val userId: String,
    val sentAt: Date, val openedAt: Date?, val deviceId: String
)
