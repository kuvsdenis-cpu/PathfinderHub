package com.pathfinder.hub.data.local.entity.notifications
import androidx.room.*
import java.util.Date

@Entity(tableName = "notifications",
    indices = [Index("userId"), Index("isRead"), Index("groupId")])
data class NotificationEntity(
    @PrimaryKey val id: String, val userId: String, val type: String,
    val title: String, val body: String, val targetType: String?,
    val targetId: String?, val deepLink: String?, val priority: String,
    val groupId: String?, val isRead: Boolean, val isHandled: Boolean,
    val createdAt: Date, val readAt: Date?, val handledAt: Date?
)
