package com.pathfinder.hub.data.local.entity.settings
import androidx.room.*
import java.util.Date

@Entity(tableName = "system_settings")
data class SystemSettingsEntity(
    @PrimaryKey val id: Int = 1, val appVersion: String,
    val contentVersion: String, val dbSchemaVersion: Int,
    val minAndroidVersion: String, val contentSyncFrequency: String,
    val backupFrequency: String, val backupRetentionMonths: Int,
    val securityLogRetentionYears: Int, val auditLogRetentionYears: Int,
    val crashlyticsEnabled: Boolean, val analyticsEnabled: Boolean,
    val appCheckRequired: Boolean, val alertsEnabled: Boolean, val updatedAt: Date
)
