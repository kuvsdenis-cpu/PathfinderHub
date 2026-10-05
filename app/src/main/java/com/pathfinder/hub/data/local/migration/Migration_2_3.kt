package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 2 → 3: добавляем колонку media_urls в level_progress.
 *
 * Хранится как TEXT (JSON-массив строк через Converters).
 * По умолчанию — пустой массив '[]'.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE level_progress ADD COLUMN mediaUrls TEXT NOT NULL DEFAULT '[]'"
        )
    }
}