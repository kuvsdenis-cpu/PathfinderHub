package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "content_versions")
data class ContentVersionEntity(
    @PrimaryKey val version: String,
    val updatedAt: Date,
    val levels: Int = 0,
    val honors: Int = 0,
    val changelog: String? = null,
    val levelsVersion: String? = null,
    val honorsVersion: String? = null,
    val testsVersion: String? = null
)
