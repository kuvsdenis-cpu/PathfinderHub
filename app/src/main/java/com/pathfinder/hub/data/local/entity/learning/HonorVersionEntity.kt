package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "honor_versions")
data class HonorVersionEntity(
    @PrimaryKey val honorId: String, val version: String, val changedAt: Date,
    val affectedRequirements: List<String>, val gracePeriodDays: Int = 30, val changelog: String?
)
