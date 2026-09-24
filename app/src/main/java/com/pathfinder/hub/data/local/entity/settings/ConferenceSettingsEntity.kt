package com.pathfinder.hub.data.local.entity.settings
import androidx.room.*
import java.util.Date

@Entity(tableName = "conference_settings")
data class ConferenceSettingsEntity(
    @PrimaryKey val conferenceId: String, val contentVersion: String,
    val syncFrequency: String = "weekly", val manualImportEnabled: Boolean,
    val localHonorsApproval: Boolean, val globalizationEnabled: Boolean,
    val hideReasons: List<String>, val moderators: List<String>,
    val escalationLevels: Int = 3, val updatedAt: Date, val updatedBy: String
)
