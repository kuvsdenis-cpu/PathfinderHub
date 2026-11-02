package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Универсальный тест. Может быть привязан к любому объекту через scope + scopeId:
 *   scope = "honor"      + scopeId = "honor_023"    — тест специализации
 *   scope = "level"      + scopeId = "level_3"      — тест ступени
 *   scope = "homework"   + scopeId = "hw_xyz"       — тест домашки
 *   scope = "quest"      + scopeId = "quest_abc"    — тест квеста
 *   scope = "event"      + scopeId = "event_123"    — тест события
 *   scope = "standalone" + scopeId = null           — самостоятельный тест
 *
 * clubId = null → глобальный тест (из tests.json).
 * clubId != null → локальный тест клуба (создан в редакторе).
 */
@Entity(
    tableName = "tests",
    indices = [Index("clubId"), Index("scope"), Index("scopeId"), Index("archivedAt")]
)
data class TestEntity(
    @PrimaryKey val id: String,
    val clubId: String?,
    val scope: String,
    val scopeId: String?,
    val title: String,
    val description: String = "",
    val passScore: Int = 90,
    val archivedAt: Date? = null,
    val purgeAfter: Date? = null,
    val pendingSync: Boolean = false,
)
