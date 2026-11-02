package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v31 -> v32: soft delete для клубов (корзина на 90 дней).
 * Добавляет deletedAt / deletedBy / purgeAt.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clubs ADD COLUMN deletedAt INTEGER")
        db.execSQL("ALTER TABLE clubs ADD COLUMN deletedBy TEXT")
        db.execSQL("ALTER TABLE clubs ADD COLUMN purgeAt INTEGER")
    }
}
