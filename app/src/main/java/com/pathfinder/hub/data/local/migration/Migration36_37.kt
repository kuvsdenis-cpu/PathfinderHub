package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reading_plan_schedules` (
                `id` TEXT NOT NULL,
                `year` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `importedAt` INTEGER NOT NULL,
                `importedBy` TEXT,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reading_plan_days` (
                `id` TEXT NOT NULL,
                `scheduleId` TEXT NOT NULL,
                `dayNumber` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `reference` TEXT NOT NULL,
                `bookId` TEXT,
                `chapterFrom` INTEGER,
                `chapterTo` INTEGER,
                `passageText` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`scheduleId`) REFERENCES `reading_plan_schedules`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_plan_days_scheduleId_dayNumber` ON `reading_plan_days` (`scheduleId`, `dayNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_plan_days_scheduleId` ON `reading_plan_days` (`scheduleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_plan_days_date` ON `reading_plan_days` (`date`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reading_plan_progress` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `dayId` TEXT NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_plan_progress_userId_dayId` ON `reading_plan_progress` (`userId`, `dayId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_plan_progress_userId` ON `reading_plan_progress` (`userId`)")
    }
}
