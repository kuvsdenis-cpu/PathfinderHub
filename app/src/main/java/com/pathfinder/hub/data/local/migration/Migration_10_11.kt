package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 10 -> 11.
 *
 * Добавляет в invites колонку conferenceId — для инвайтов
 * на роль conference_secretary (привязка к конференции,
 * а не к клубу).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invites ADD COLUMN conferenceId TEXT")
    }
}
