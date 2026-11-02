package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Стих Библии. id = "rst:Gen:1:1".
 * versionId денормализован для быстрых запросов без JOIN.
 */
@Entity(
    tableName = "bible_verses",
    foreignKeys = [ForeignKey(
        entity = BibleBookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("bookId"),
        Index(value = ["bookId", "chapter", "verse"], unique = true)
    ]
)
data class BibleVerseEntity(
    @PrimaryKey val id: String,          // "rst:Gen:1:1"
    val versionId: String,               // "rst"
    val bookId: String,                  // "rst:Gen"
    val chapter: Int,
    val verse: Int,
    val text: String
)
