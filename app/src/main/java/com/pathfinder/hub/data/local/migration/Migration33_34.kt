package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v33 -> v34: Библия (Синодальный перевод).
 * Добавляет таблицы: bible_versions, bible_books, bible_verses.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bible_versions` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `language` TEXT NOT NULL,
                `copyright` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bible_books` (
                `id` TEXT NOT NULL,
                `versionId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `shortName` TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                `chaptersCount` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`versionId`) REFERENCES `bible_versions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bible_books_versionId` ON `bible_books`(`versionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bible_books_versionId_order` ON `bible_books`(`versionId`, `order`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bible_verses` (
                `id` TEXT NOT NULL,
                `versionId` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `chapter` INTEGER NOT NULL,
                `verse` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `bible_books`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bible_verses_bookId` ON `bible_verses`(`bookId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bible_verses_bookId_chapter_verse` ON `bible_verses`(`bookId`, `chapter`, `verse`)")
    }
}
