package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bible_bookmarks",
    indices = [
        Index(value = ["userId", "verseId"], unique = true),
        Index("userId")
    ]
)
data class BibleBookmarkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val verseId: String,
    val versionId: String,
    val bookId: String,
    val bookName: String = "",
    val chapter: Int,
    val verse: Int,
    val textPreview: String,
    val note: String? = null,
    val createdAt: Long,
    val pendingSync: Boolean = true
)
