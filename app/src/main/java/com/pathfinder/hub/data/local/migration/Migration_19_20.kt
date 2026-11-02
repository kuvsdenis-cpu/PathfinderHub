package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 19 -> 20.
 * Добавляет поля архивации в comments.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comments ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE comments ADD COLUMN purgeAfter INTEGER")
    }
}
