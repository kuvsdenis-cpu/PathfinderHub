package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*
import java.util.Date

@Entity(tableName = "cache_settings")
data class CacheSettingsEntity(
    @PrimaryKey val id: Int = 1, val autoCleanEnabled: Boolean,
    val autoCleanThresholdMb: Int, val lastCleanAt: Date?
)
