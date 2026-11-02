package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v52 → v53: books.contentPreview (TEXT, nullable) — превью текста для списка.
 */
val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN contentPreview TEXT")
    }
}
