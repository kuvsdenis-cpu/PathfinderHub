package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*
import java.util.Date

@Entity(tableName = "sync_queue", indices = [Index("status"), Index("priority")])
data class SyncQueueItemEntity(
    @PrimaryKey val id: String, val userId: String, val operation: String,
    val entityType: String, val entityId: String, val payload: String,
    val status: String, val retryCount: Int, val lastAttemptAt: Date?,
    val errorMessage: String?, val createdAt: Date, val priority: String
)
