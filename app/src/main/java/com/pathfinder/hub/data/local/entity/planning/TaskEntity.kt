package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*
import java.util.Date

@Entity(tableName = "tasks",
    indices = [Index("clubId"), Index("eventId"), Index("assignedTo"), Index("status")])
data class TaskEntity(
    @PrimaryKey val id: String, val level: String, val clubId: String?,
    val eventId: String?, val title: String, val description: String,
    val assignedTo: String, val assignedBy: String, val dueDate: Date?,
    val priority: String, val status: String, val createdAt: Date,
    val confirmedBy: String?, val confirmedAt: Date?, val pendingSync: Boolean = false
)
