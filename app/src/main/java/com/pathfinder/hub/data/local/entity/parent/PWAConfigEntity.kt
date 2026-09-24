package com.pathfinder.hub.data.local.entity.parent
import androidx.room.*

@Entity(tableName = "pwa_config")
data class PWAConfigEntity(
    @PrimaryKey val id: Int = 1, val enabled: Boolean, val url: String,
    val features: List<String>, val pushEnabled: Boolean,
    val offlineEnabled: String, val installable: Boolean
)
