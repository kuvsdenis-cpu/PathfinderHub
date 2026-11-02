package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v38 -> v39: tombstone для comments (удаление с 90-дневной задержкой).
 * Добавляет deletedAt / deletedBy / purgeAt.
 *
 * events и tasks не трогаем — у них уже есть архивация (archivedAt / purgeAfter),
 * которая покрывает soft-delete.
 */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comments ADD COLUMN deletedAt INTEGER")
        db.execSQL("ALTER TABLE comments ADD COLUMN deletedBy TEXT")
        db.execSQL("ALTER TABLE comments ADD COLUMN purgeAt INTEGER")
    }
}
