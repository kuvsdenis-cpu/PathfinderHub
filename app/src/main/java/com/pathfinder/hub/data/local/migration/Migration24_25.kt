package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 24 -> 25.
 * A3: questions в event_participants (родительские вопросы на встречах).
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE event_participants ADD COLUMN questions TEXT NOT NULL DEFAULT '[]'")
    }
}
