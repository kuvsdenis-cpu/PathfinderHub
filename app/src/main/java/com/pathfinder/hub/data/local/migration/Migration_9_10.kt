package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 9 -> 10.
 *
 * Добавляет таблицы QR-квестов:
 *   - qr_quests           (сами квесты)
 *   - qr_quest_stations   (связи квест-станция с порядком)
 *   - qr_quest_progress   (прогресс teen по квесту)
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_quests` (
                `id` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `rewardPoints` INTEGER NOT NULL,
                `rewardAchievementId` TEXT,
                `dateStart` INTEGER,
                `dateEnd` INTEGER,
                `status` TEXT NOT NULL,
                `createdBy` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quests_clubId` ON `qr_quests` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quests_status` ON `qr_quests` (`status`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_quest_stations` (
                `questId` TEXT NOT NULL,
                `stationId` TEXT NOT NULL,
                `stepIndex` INTEGER NOT NULL,
                PRIMARY KEY(`questId`, `stationId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_stations_questId` ON `qr_quest_stations` (`questId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_stations_stationId` ON `qr_quest_stations` (`stationId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_quest_progress` (
                `questId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `lastCompletedStepIndex` INTEGER NOT NULL,
                `completedStepIndices` TEXT NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `rewardClaimed` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`questId`, `userId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_progress_questId` ON `qr_quest_progress` (`questId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_progress_userId` ON `qr_quest_progress` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_progress_clubId` ON `qr_quest_progress` (`clubId`)")
    }
}
