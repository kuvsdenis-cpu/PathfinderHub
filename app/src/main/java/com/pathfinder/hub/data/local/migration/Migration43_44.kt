package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 43 -> 44.
 * B8: интерактивная тренировка Жемчужин.
 * pearl_progress: +trainingScore (INTEGER, nullable),
 *                 +trainingAttempts (INTEGER NOT NULL DEFAULT 0),
 *                 +lastTrainedAt (INTEGER, nullable).
 */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pearl_progress ADD COLUMN trainingScore INTEGER")
        db.execSQL("ALTER TABLE pearl_progress ADD COLUMN trainingAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pearl_progress ADD COLUMN lastTrainedAt INTEGER")
    }
}
