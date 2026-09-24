package com.pathfinder.hub.data.local.entity.newspaper
import androidx.room.*
import java.util.Date

@Entity(tableName = "cached_newspapers")
data class CachedNewspaperEntity(
    @PrimaryKey val issueId: String, val localPath: String,
    val downloadedAt: Date, val fileSizeBytes: Int
)
