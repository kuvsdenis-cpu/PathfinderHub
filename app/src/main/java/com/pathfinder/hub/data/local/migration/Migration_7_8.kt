package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 7 -> 8.
 *
 * Добавляет clubIds (JSON-массив строк) в users.
 * Заполняется clubId для существующих пользователей.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN clubIds TEXT NOT NULL DEFAULT '[]'")
        // Заполняем clubIds из clubId для уже существующих
        db.execSQL(
            """
            UPDATE users
            SET clubIds = '["' || clubId || '"]'
            WHERE clubId IS NOT NULL AND clubId != ''
            """.trimIndent()
        )
    }
}
