package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*
import java.util.Date

@Entity(tableName = "media_sync_queue")
data class MediaSyncQueueEntity(
    @PrimaryKey val id: String, val userId: String, val entityType: String,
    val entityId: String, val fileUri: String, val fileSize: Int,
    val status: String, val retryCount: Int, val createdAt: Date
)
