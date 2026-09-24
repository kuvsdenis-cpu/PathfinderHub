package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "honor_drafts")
data class HonorDraftEntity(
    @PrimaryKey val id: String, val authorId: String, val clubId: String,
    val name: String, val category: String, val level: Int, val description: String,
    val requirements: List<String>, val questions: List<String>, val status: String,
    val reviewedBy: String?, val reviewedAt: Date?, val rejectionReason: String?,
    val createdAt: Date
)
