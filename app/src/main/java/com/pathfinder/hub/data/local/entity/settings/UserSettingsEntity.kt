package com.pathfinder.hub.data.local.entity.settings
import androidx.room.*
import java.util.Date

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val userId: String, val theme: String,
    val language: String = "ru", val updatedAt: Date
)
