package com.pathfinder.hub.data.local.entity.parent
import androidx.room.*
import java.util.Date

@Entity(tableName = "pwa_install_events")
data class PWAInstallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String, val installedAt: Date, val platform: String, val browser: String
)
