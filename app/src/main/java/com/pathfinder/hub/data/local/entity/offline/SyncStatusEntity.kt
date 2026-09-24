package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*
import java.util.Date

@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey val id: Int = 1, val isOnline: Boolean, val isSyncing: Boolean,
    val queueSize: Int, val lastSyncAt: Date?, val lastError: String?, val conflictsCount: Int
)
