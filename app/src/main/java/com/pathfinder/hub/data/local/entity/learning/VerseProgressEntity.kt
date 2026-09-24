package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*
import java.util.Date

@Entity(tableName = "verse_progress", primaryKeys = ["userId", "verseId"])
data class VerseProgressEntity(
    val userId: String, val verseId: String, val status: String,
    val approvedBy: String?, val approvedAt: Date?, val pendingSync: Boolean = false
)
