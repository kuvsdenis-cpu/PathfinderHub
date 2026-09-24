package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*
import java.util.Date

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val key: String, val type: String, val sizeBytes: Int,
    val lastUpdated: Date, val expiresAt: Date?
)
