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
    val confirmedBy: String?, val confirmedAt: Date?, val pendingSync: Boolean = false,

    // S4: QR
    val qrCode: String? = null,

    // S7: Архивация
    val archivedAt: Date? = null,
    val purgeAfter: Date? = null,

    // A1: Групповое назначение
    // null - назначено конкретному (assignedTo)
    // "all_teens" | "all_parents" | "all_staff" | "all_members"
    // "role:teen,instructor" - кастом
    val targetGroup: String? = null,
)
