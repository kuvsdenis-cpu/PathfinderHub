package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "content_versions")
data class ContentVersionEntity(
    @PrimaryKey val version: String, val updatedAt: Date,
    val levels: Int, val honors: Int, val changelog: String?
)
