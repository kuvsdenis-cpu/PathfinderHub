package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v32 -> v33: передача владения клубом через инвайт.
 * Добавляет invites.transferOwnership (INTEGER, NOT NULL, DEFAULT 0).
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invites ADD COLUMN transferOwnership INTEGER NOT NULL DEFAULT 0")
    }
}
