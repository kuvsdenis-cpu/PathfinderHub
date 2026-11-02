package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 20 -> 21.
 * Добавляет поля архивации в qr_quests и qr_stations.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE qr_quests ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE qr_quests ADD COLUMN purgeAfter INTEGER")
        db.execSQL("ALTER TABLE qr_stations ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE qr_stations ADD COLUMN purgeAfter INTEGER")
    }
}
