package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bible_bookmarks` ADD COLUMN `bookName` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `bible_bookmarks` ADD COLUMN `note` TEXT")
    }
}
