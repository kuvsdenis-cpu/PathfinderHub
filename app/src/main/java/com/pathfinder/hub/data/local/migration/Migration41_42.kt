package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 41 -> 42:
 * books: +externalUrl, +contentPath, +coverUrl, +clubId
 * level_requirements: +bookIds (JSON TEXT)
 * honor_requirements: +bookIds (JSON TEXT)
 */
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN externalUrl TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN contentPath TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN coverUrl TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN clubId TEXT")

        db.execSQL("ALTER TABLE level_requirements ADD COLUMN bookIds TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE honor_requirements ADD COLUMN bookIds TEXT NOT NULL DEFAULT '[]'")
    }
}
