package com.pathfinder.hub.data.local.entity.settings
import androidx.room.*
import java.util.Date

@Entity(tableName = "club_settings")
data class ClubSettingsEntity(
    @PrimaryKey val clubId: String, val updatedAt: Date, val updatedBy: String
)
