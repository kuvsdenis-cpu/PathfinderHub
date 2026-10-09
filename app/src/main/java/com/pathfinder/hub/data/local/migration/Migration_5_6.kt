package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 5 -> 6.
 *
 * Добавляет три таблицы:
 *   - requirement_materials       (сопутствующие материалы)
 *   - requirement_tasks           (проверочные задания)
 *   - requirement_task_answers    (ответы следопытов)
 *
 * Существующие таблицы не трогаем.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // -------- requirement_materials --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `requirement_materials` (
                `id` TEXT NOT NULL,
                `requirementId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `flaggedForEdit` INTEGER NOT NULL DEFAULT 0,
                `flaggedBy` TEXT,
                `flaggedAt` INTEGER,
                `flagComment` TEXT,
                `createdBy` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_requirementId` ON `requirement_materials` (`requirementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_clubId` ON `requirement_materials` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_flaggedForEdit` ON `requirement_materials` (`flaggedForEdit`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_requirementId_clubId` ON `requirement_materials` (`requirementId`, `clubId`)")

        // -------- requirement_tasks --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `requirement_tasks` (
                `id` TEXT NOT NULL,
                `requirementId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `options` TEXT NOT NULL DEFAULT '',
                `correctAnswer` TEXT,
                `autoCheck` INTEGER NOT NULL DEFAULT 0,
                `isRequired` INTEGER NOT NULL DEFAULT 1,
                `orderIndex` INTEGER NOT NULL,
                `flaggedForEdit` INTEGER NOT NULL DEFAULT 0,
                `flaggedBy` TEXT,
                `flaggedAt` INTEGER,
                `flagComment` TEXT,
                `createdBy` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_requirementId` ON `requirement_tasks` (`requirementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_clubId` ON `requirement_tasks` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_flaggedForEdit` ON `requirement_tasks` (`flaggedForEdit`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_requirementId_clubId` ON `requirement_tasks` (`requirementId`, `clubId`)")

        // -------- requirement_task_answers --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `requirement_task_answers` (
                `id` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `requirementId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `answer` TEXT NOT NULL,
                `attachments` TEXT NOT NULL DEFAULT '',
                `status` TEXT NOT NULL,
                `autoChecked` INTEGER NOT NULL DEFAULT 0,
                `submittedAt` INTEGER,
                `reviewedBy` TEXT,
                `reviewedAt` INTEGER,
                `reviewComment` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_taskId` ON `requirement_task_answers` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_requirementId` ON `requirement_task_answers` (`requirementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_userId` ON `requirement_task_answers` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_clubId` ON `requirement_task_answers` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_status` ON `requirement_task_answers` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_requirement_task_answers_taskId_userId` ON `requirement_task_answers` (`taskId`, `userId`)")
    }
}
