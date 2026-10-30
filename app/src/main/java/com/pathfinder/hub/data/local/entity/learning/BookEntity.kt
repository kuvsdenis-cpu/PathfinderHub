package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val level: Int,
    val description: String?,
    val externalUrl: String? = null,
    val contentPath: String? = null,
    val contentUrl: String? = null,
    val contentPreview: String? = null,
    val coverUrl: String? = null,
    val clubId: String? = null,
    val createdBy: String? = null,
    val createdAt: Long? = null,
    val pendingSync: Boolean = false,
    val deletedAt: Date? = null
)
