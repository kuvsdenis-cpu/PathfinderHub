package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*
import java.util.Date

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String, val userId: String, val entityType: String,
    val entityId: String, val localValue: String, val serverValue: String,
    val localUpdatedAt: Date, val serverUpdatedAt: Date, val strategy: String,
    val status: String, val resolvedBy: String?, val resolvedAt: Date?, val resolution: String?
)
