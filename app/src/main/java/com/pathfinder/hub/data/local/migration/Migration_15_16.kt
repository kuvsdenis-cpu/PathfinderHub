package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 15 -> 16.
 *
 *   events — добавляет qrCode, latitude, longitude
 *   tasks  — добавляет qrCode
 *   homework — пересоздаёт таблицу: убирает reward, добавляет
 *              rewardType / rewardText / rewardPoints / rewardAchievementId.
 *
 * SQLite не умеет DROP COLUMN в старых версиях, поэтому homework
 * пересоздаётся полностью: CREATE new → INSERT SELECT → DROP old → RENAME.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---------- events ----------
        db.execSQL("ALTER TABLE events ADD COLUMN qrCode TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE events ADD COLUMN longitude REAL")

        // ---------- tasks ----------
        db.execSQL("ALTER TABLE tasks ADD COLUMN qrCode TEXT")

        // ---------- homework: пересоздание ----------
        // 1. Новая таблица — без reward, с новыми полями
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `homework_new` (
                `id` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `childId` TEXT NOT NULL,
                `instructorId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `dueDate` INTEGER NOT NULL,
                `rewardType` TEXT NOT NULL DEFAULT 'text',
                `rewardText` TEXT,
                `rewardPoints` INTEGER NOT NULL DEFAULT 0,
                `rewardAchievementId` TEXT,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `approvedAt` INTEGER,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // 2. Перенос данных: старый reward -> rewardText
        db.execSQL(
            """
            INSERT INTO homework_new (
                id, clubId, childId, instructorId, title, description,
                dueDate, rewardType, rewardText, rewardPoints,
                rewardAchievementId, status, createdAt, approvedAt, pendingSync
            )
            SELECT
                id, clubId, childId, instructorId, title, description,
                dueDate,
                'text',
                reward,
                0,
                NULL,
                status, createdAt, approvedAt, pendingSync
            FROM homework
            """.trimIndent()
        )

        // 3. Удаляем старую таблицу
        db.execSQL("DROP TABLE homework")

        // 4. Переименовываем новую
        db.execSQL("ALTER TABLE homework_new RENAME TO homework")
    }
}
