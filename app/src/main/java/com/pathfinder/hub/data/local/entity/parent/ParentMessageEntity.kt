package com.pathfinder.hub.data.local.entity.parent
import androidx.room.*
import java.util.Date

@Entity(tableName = "parent_messages")
data class ParentMessageEntity(
    @PrimaryKey val id: String, val parentId: String, val childId: String,
    val clubId: String, val directorId: String, val subject: String,
    val message: String, val status: String, val moderatedBy: String?,
    val moderatedAt: Date?, val respondedAt: Date?, val response: String?,
    val createdAt: Date, val pendingSync: Boolean = false
)
