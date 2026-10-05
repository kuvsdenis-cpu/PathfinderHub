package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 3 → 4: добавляем колонку imageUrls в level_requirements.
 *
 * Хранится как TEXT (JSON-массив строк через Converters).
 * NOT NULL + DEFAULT '[]' — совпадает с дефолтом `emptyList()`
 * в LevelRequirementEntity, чтобы существующие строки не стали NULL.
 *
 * Индексы (levelId, sectionId, levelId+sectionId) в v3 уже были —
 * добавлять их НЕ нужно, иначе Room начнёт ругаться на несовпадение схемы.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `level_requirements` ADD COLUMN `imageUrls` TEXT NOT NULL DEFAULT '[]'"
        )
    }
}