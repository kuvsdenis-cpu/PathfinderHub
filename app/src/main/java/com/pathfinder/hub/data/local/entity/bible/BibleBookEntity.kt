package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Книга Библии. id = "rst:Gen".
 */
@Entity(
    tableName = "bible_books",
    foreignKeys = [ForeignKey(
        entity = BibleVersionEntity::class,
        parentColumns = ["id"],
        childColumns = ["versionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("versionId"),
        Index(value = ["versionId", "order"], unique = true)
    ]
)
data class BibleBookEntity(
    @PrimaryKey val id: String,          // "rst:Gen"
    val versionId: String,               // "rst"
    val name: String,                    // "Бытие"
    val shortName: String,               // "Быт"
    val order: Int,                      // 1
    val chaptersCount: Int               // 50
)
