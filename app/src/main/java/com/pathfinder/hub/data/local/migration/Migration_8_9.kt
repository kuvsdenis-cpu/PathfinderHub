package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 8 -> 9.
 *
 * Добавляет в users:
 *   - isOwnerClub (BOOLEAN, default 1) — существующие директора становятся owner
 *   - directorPermissions (TEXT, JSON-массив строк)
 *
 * Добавляет в invites:
 *   - permissions (TEXT, JSON-массив строк)
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN isOwnerClub INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE users ADD COLUMN directorPermissions TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE invites ADD COLUMN permissions TEXT NOT NULL DEFAULT '[]'")
    }
}
