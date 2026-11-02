package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 23 -> 24.
 * A2: targetGroup в events + таблица event_participants (per-user статус).
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) events.targetGroup
        db.execSQL("ALTER TABLE events ADD COLUMN targetGroup TEXT")

        // 2) event_participants
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS event_participants (
                id TEXT NOT NULL,
                eventId TEXT NOT NULL,
                userId TEXT NOT NULL,
                status TEXT NOT NULL,
                invitedAt INTEGER NOT NULL,
                respondedAt INTEGER,
                attendedAt INTEGER,
                pendingSync INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_event_participants_eventId ON event_participants(eventId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_event_participants_userId ON event_participants(userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_event_participants_status ON event_participants(status)")
    }
}
