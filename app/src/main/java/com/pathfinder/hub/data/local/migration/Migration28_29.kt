package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 28 -> 29. Синхронизация клубных тестов.
 *
 *   - test_questions: добавляем clubId (nullable)
 *
 * Глобальные тесты (из tests.json) остаются с clubId = NULL.
 * Клубные тесты (созданные директором/инструктором) — clubId = <клуб>.
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `test_questions` ADD COLUMN `clubId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_questions_clubId` ON `test_questions` (`clubId`)")
    }
}
