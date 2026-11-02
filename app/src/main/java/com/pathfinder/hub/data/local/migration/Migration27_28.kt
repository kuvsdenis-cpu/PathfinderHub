package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 27 -> 28. Вводим универсальные тесты.
 *
 *   - новая таблица tests
 *   - test_questions: добавляем testId (nullable) и order
 *   - test_attempts:  добавляем testId (nullable)
 *
 * testId nullable — чтобы существующие записи не нарушали схему.
 * Заполнение testId для старых записей — отдельная задача.
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---------- tests ----------
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tests` (
                `id` TEXT NOT NULL,
                `clubId` TEXT,
                `scope` TEXT NOT NULL,
                `scopeId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `passScore` INTEGER NOT NULL DEFAULT 90,
                `archivedAt` INTEGER,
                `purgeAfter` INTEGER,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tests_clubId` ON `tests` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tests_scope` ON `tests` (`scope`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tests_scopeId` ON `tests` (`scopeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tests_archivedAt` ON `tests` (`archivedAt`)")

        // ---------- test_questions ----------
        db.execSQL("ALTER TABLE `test_questions` ADD COLUMN `testId` TEXT")
        db.execSQL("ALTER TABLE `test_questions` ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_questions_testId` ON `test_questions` (`testId`)")

        // ---------- test_attempts ----------
        db.execSQL("ALTER TABLE `test_attempts` ADD COLUMN `testId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_attempts_testId` ON `test_attempts` (`testId`)")
    }
}
