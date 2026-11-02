package com.pathfinder.hub.data.local.entity.bible

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Версия Библии. Пока одна: "rst" (Синодальный перевод).
 */
@Entity(tableName = "bible_versions")
data class BibleVersionEntity(
    @PrimaryKey val id: String,          // "rst"
    val name: String,                    // "Синодальный перевод"
    val language: String,                // "ru"
    val copyright: String?,              // "Public Domain"
    val createdAt: Long
)
