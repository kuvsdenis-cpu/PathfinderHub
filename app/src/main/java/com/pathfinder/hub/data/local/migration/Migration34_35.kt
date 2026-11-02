package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bible_bookmarks` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `verseId` TEXT NOT NULL,
                `versionId` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `chapter` INTEGER NOT NULL,
                `verse` INTEGER NOT NULL,
                `textPreview` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bible_bookmarks_userId` " +
                "ON `bible_bookmarks` (`userId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bible_bookmarks_userId_verseId` " +
                "ON `bible_bookmarks` (`userId`, `verseId`)"
        )
    }
}
