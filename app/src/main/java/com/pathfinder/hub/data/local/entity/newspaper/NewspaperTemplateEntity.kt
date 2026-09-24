package com.pathfinder.hub.data.local.entity.newspaper
import androidx.room.*
import java.util.Date

@Entity(tableName = "newspaper_templates")
data class NewspaperTemplateEntity(
    @PrimaryKey val id: String, val name: String, val level: String,
    val sections: List<TemplateSection>, val createdBy: String,
    val createdAt: Date, val isDefault: Boolean
)

data class TemplateSection(
    val id: String, val name: String, val order: Int,
    val required: Boolean, val blocks: List<String>, val maxArticles: Int?
)
