package com.pathfinder.hub.data.local.entity.reports
import androidx.room.*
import java.util.Date

@Entity(tableName = "report_templates")
data class ReportTemplateEntity(
    @PrimaryKey val id: String, val name: String, val level: String,
    val type: String, val sections: List<String>, val format: String = "pdf",
    val isDefault: Boolean, val createdBy: String, val createdAt: Date
)
