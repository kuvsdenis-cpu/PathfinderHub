package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*

@Entity(tableName = "sync_settings")
data class SyncSettingsEntity(
    @PrimaryKey val id: Int = 1, val autoSyncEnabled: Boolean, val wifiOnly: Boolean,
    val backgroundSync: Boolean, val autoSyncInterval: Int,
    val manualSyncAvailable: Boolean, val hideForegroundNotification: Boolean
)
