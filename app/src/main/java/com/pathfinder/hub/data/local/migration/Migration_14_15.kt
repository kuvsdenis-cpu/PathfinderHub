package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 14 -> 15. Мероприятия конференции.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conference_events` (
                `id` TEXT NOT NULL,
                `conferenceId` TEXT NOT NULL,
                `level` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `dateStart` INTEGER NOT NULL,
                `dateEnd` INTEGER NOT NULL,
                `timezone` TEXT NOT NULL,
                `location` TEXT NOT NULL,
                `invitedClubs` TEXT NOT NULL,
                `visibility` TEXT NOT NULL,
                `createdBy` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `publishedAt` INTEGER,
                `cancelledReason` TEXT,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conference_events_conferenceId` ON `conference_events` (`conferenceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conference_events_status` ON `conference_events` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_conference_events_dateStart` ON `conference_events` (`dateStart`)")
    }
}
