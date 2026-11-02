package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 12 -> 13.
 * Добавляет documentUrl в event_consents — ссылка на фото подписанного
 * согласия в Yandex Object Storage.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE event_consents ADD COLUMN documentUrl TEXT")
    }
}
