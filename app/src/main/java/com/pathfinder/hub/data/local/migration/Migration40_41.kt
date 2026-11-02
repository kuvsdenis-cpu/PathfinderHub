package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 40 → 41.
 * Раздельные версии контента: levelsVersion / honorsVersion / testsVersion.
 * Раньше была одна колонка `version` (maxOf всех источников), из-за чего
 * изменение одного файла блокировало обновление других.
 */
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE content_versions ADD COLUMN levelsVersion TEXT")
        database.execSQL("ALTER TABLE content_versions ADD COLUMN honorsVersion TEXT")
        database.execSQL("ALTER TABLE content_versions ADD COLUMN testsVersion TEXT")
    }
}
