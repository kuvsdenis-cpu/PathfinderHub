package com.pathfinder.hub.data.local.entity.info

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "info_articles",
    indices = [Index("categoryId"), Index("pendingSync")]
)
data class InfoArticleEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val title: String,
    val content: String,
    val pdfUrl: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false,
    val deletedAt: Date? = null
)
