package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 11 -> 12. Добавляет event_consents.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `event_consents` (
                `eventId` TEXT NOT NULL,
                `childId` TEXT NOT NULL,
                `parentId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `requestedBy` TEXT NOT NULL,
                `requestedAt` INTEGER NOT NULL,
                `respondedAt` INTEGER,
                `comment` TEXT,
                PRIMARY KEY(`eventId`, `childId`, `parentId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_consents_eventId` ON `event_consents` (`eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_consents_childId` ON `event_consents` (`childId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_consents_parentId` ON `event_consents` (`parentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_consents_status` ON `event_consents` (`status`)")
    }
}
