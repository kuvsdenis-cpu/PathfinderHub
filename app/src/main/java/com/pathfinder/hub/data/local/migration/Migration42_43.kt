package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 42 -> 43:
 * books: +createdBy, +createdAt, +pendingSync
 * Для клубных книг (sync с Firestore).
 */
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN createdBy TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN createdAt INTEGER")
        db.execSQL("ALTER TABLE books ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
    }
}
