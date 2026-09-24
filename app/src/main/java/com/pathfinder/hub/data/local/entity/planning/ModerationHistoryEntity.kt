package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "moderation_history", indices = [Index("targetType", "targetId")])
data class ModerationHistoryEntity(
    @PrimaryKey val id: String, val targetType: String, val targetId: String,
    val action: String, val userId: String, val timestamp: Date,
    val oldValue: String?, val newValue: String?, val visibleToAuthor: Boolean
)
