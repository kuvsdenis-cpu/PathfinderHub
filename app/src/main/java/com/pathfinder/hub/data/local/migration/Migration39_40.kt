package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE honors ADD COLUMN previewUrl TEXT")
        database.execSQL("ALTER TABLE honors ADD COLUMN imageUrl TEXT")
    }
}
