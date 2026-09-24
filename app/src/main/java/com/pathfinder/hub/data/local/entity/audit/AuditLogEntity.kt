package com.pathfinder.hub.data.local.entity.audit
import androidx.room.*
import java.util.Date

@Entity(tableName = "audit_log",
    indices = [Index("targetType", "targetId"), Index("userId"), Index("timestamp")])
data class AuditLogEntity(
    @PrimaryKey val id: String, val targetType: String, val targetId: String,
    val action: String, val userId: String, val userRole: String,
    val clubId: String?, val conferenceId: String?, val timestamp: Date,
    val oldValue: String?, val newValue: String?, val reason: String?,
    val visibleToAuthor: Boolean, val ipHash: String?, val userAgent: String?
)
