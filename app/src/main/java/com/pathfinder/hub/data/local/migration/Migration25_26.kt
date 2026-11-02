package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 25 -> 26.
 * B1: таблица custom_groups для пользовательских групп с динамическими правилами.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_groups (
                id TEXT NOT NULL,
                clubId TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                createdBy TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                ruleJson TEXT NOT NULL,
                archivedAt INTEGER,
                purgeAfter INTEGER,
                pendingSync INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_groups_clubId ON custom_groups(clubId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_groups_createdBy ON custom_groups(createdBy)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_groups_archivedAt ON custom_groups(archivedAt)")
    }
}
