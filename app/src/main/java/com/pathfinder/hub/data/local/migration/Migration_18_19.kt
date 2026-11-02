package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 18 -> 19.
 * Добавляет поля архивации в tasks и homework.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN purgeAfter INTEGER")
        db.execSQL("ALTER TABLE homework ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE homework ADD COLUMN purgeAfter INTEGER")
    }
}
