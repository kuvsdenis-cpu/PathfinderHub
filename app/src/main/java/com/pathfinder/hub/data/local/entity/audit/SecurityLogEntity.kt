package com.pathfinder.hub.data.local.entity.audit
import androidx.room.*
import java.util.Date

@Entity(tableName = "security_log", indices = [Index("userId"), Index("timestamp")])
data class SecurityLogEntity(
    @PrimaryKey val id: String, val userId: String, val action: String,
    val ipHash: String, val userAgent: String, val timestamp: Date,
    val success: Boolean, val details: String?
)
