package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 21 -> 22.
 * Добавляет поля архивации в reports.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reports ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE reports ADD COLUMN purgeAfter INTEGER")
    }
}
