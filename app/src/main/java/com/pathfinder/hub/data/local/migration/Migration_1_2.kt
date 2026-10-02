package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 1 → 2.
 *
 * Изменения:
 * - level_sections: PK стал составным (levelId, id) + FK на levels(id) с CASCADE.
 * - level_requirements: FK теперь ссылается на составной PK level_sections(levelId, id).
 *
 * Данные level_sections и level_requirements ОЧИЩАЮТСЯ — контент перезагрузится
 * из assets через SeedDatabaseWorker (версия levels.json будет поднята до 2.1).
 *
 * Данные level_progress и level_completions НЕ ТРОГАЕМ — прогресс пользователя сохраняется.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Удаляем старые таблицы (порядок важен из-за FK)
        db.execSQL("DROP TABLE IF EXISTS level_requirements")
        db.execSQL("DROP TABLE IF EXISTS level_sections")

        // 2. Создаём level_sections с составным PK
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `level_sections` (
                `id` TEXT NOT NULL,
                `levelId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                PRIMARY KEY(`levelId`, `id`),
                FOREIGN KEY(`levelId`) REFERENCES `levels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // 3. Индекс на levelId (для запросов observeSections/getSections)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_level_sections_levelId` ON `level_sections` (`levelId`)"
        )

        // 4. Создаём level_requirements с составным FK
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `level_requirements` (
                `id` TEXT NOT NULL,
                `sectionId` TEXT NOT NULL,
                `levelId` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                `isAdvanced` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`levelId`, `sectionId`) REFERENCES `level_sections`(`levelId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // 5. Индексы для level_requirements
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_level_requirements_levelId` ON `level_requirements` (`levelId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_level_requirements_sectionId` ON `level_requirements` (`sectionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_level_requirements_levelId_sectionId` ON `level_requirements` (`levelId`, `sectionId`)"
        )
    }
}