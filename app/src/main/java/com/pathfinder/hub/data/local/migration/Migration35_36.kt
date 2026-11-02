package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pearl_schedules` (
                `id` TEXT NOT NULL,
                `year` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `importedAt` INTEGER NOT NULL,
                `importedBy` TEXT,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pearls` (
                `id` TEXT NOT NULL,
                `scheduleId` TEXT NOT NULL,
                `weekNumber` INTEGER NOT NULL,
                `season` INTEGER NOT NULL,
                `seasonTitle` TEXT NOT NULL,
                `verseId` TEXT,
                `reference` TEXT NOT NULL,
                `text` TEXT,
                `startDate` INTEGER NOT NULL,
                `endDate` INTEGER NOT NULL,
                `isExam` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`scheduleId`) REFERENCES `pearl_schedules`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pearls_scheduleId_weekNumber` ON `pearls` (`scheduleId`, `weekNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pearls_scheduleId` ON `pearls` (`scheduleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pearls_verseId` ON `pearls` (`verseId`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pearl_progress` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `pearlId` TEXT NOT NULL,
                `memorized` INTEGER NOT NULL DEFAULT 0,
                `memorizedAt` INTEGER,
                `lastReviewedAt` INTEGER,
                `reviewCount` INTEGER NOT NULL DEFAULT 0,
                `pendingSync` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pearl_progress_userId_pearlId` ON `pearl_progress` (`userId`, `pearlId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pearl_progress_userId` ON `pearl_progress` (`userId`)")
    }
}
