package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "honors", indices = [Index("categoryId")])
data class HonorEntity(
    @PrimaryKey val id: String, val name: String, val categoryId: String,
    val level: Int, val year: Int, val source: String, val description: String,
    val version: String, val isLocal: Boolean = false, val localClubId: String?,
    val scope: String = "global", val promotedAt: Date?, val promotedBy: String?,
    val contentVersion: String
)
