package com.pathfinder.hub.data.local.migration
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Будущие изменения
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
