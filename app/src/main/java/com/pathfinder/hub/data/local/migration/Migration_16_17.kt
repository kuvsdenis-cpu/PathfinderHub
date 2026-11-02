package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 16 -> 17.
 *
 * До этой миграции MIGRATION_15_16 (старая версия) добавляла к homework
 * колонки rewardType/rewardText/rewardPoints/rewardAchievementId,
 * НО НЕ УДАЛЯЛА старую колонку reward. Room падал на проверке схемы:
 * "Migration didn't properly handle: homework".
 *
 * Эта миграция пересоздаёт homework без колонки reward.
 * Данные из новых колонок сохраняются, старая reward — теряется
 * (её содержимое уже скопировано в rewardText).
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Новая таблица — без reward
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

        // 2. Перенос данных из старой таблицы со всеми 4 колонками
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
                COALESCE(rewardType, 'text'),
                rewardText,
                COALESCE(rewardPoints, 0),
                rewardAchievementId,
                status, createdAt, approvedAt, pendingSync
            FROM homework
            """.trimIndent()
        )

        // 3. Удаляем старую, переименовываем новую
        db.execSQL("DROP TABLE homework")
        db.execSQL("ALTER TABLE homework_new RENAME TO homework")
    }
}
