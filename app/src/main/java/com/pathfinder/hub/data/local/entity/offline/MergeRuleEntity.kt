package com.pathfinder.hub.data.local.entity.offline
import androidx.room.*

@Entity(tableName = "merge_rules")
data class MergeRuleEntity(
    @PrimaryKey val entityType: String, val strategy: String
)
