package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 17 -> 18.
 * Добавляет в events поля архивации: archivedAt, purgeAfter.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN purgeAfter INTEGER")
    }
}
