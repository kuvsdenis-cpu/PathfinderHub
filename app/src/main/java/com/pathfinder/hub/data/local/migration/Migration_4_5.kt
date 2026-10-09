package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 4 → 5: добавляем колонку clubId в comments.
 *
 * Зачем: комментарии и сообщения чата должны синхронизироваться
 * в Firestore в коллекцию clubs/{clubId}/comments. Без clubId
 * SyncWorker не мог вычислить путь коллекции и падал с PERMANENT_FAILURE.
 *
 * NOT NULL + DEFAULT '' — старые комментарии получат пустой clubId.
 * Для чата новые комментарии будут писаться с реальным clubId.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `comments` ADD COLUMN `clubId` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_comments_clubId` ON `comments` (`clubId`)"
        )
    }
}