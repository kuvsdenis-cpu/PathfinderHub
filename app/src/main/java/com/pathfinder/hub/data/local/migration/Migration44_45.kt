package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 44 -> 45.
 * bookIds: NOT NULL -> nullable.
 * SQLite не умеет ALTER COLUMN, поэтому пересоздаём таблицу:
 *   CREATE new -> INSERT SELECT -> DROP old -> RENAME -> индексы.
 */
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `level_requirements_new` (
                `id` TEXT NOT NULL,
                `levelId` TEXT NOT NULL,
                `sectionId` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                `isAdvanced` INTEGER NOT NULL,
                `imageUrls` TEXT NOT NULL,
                `bookIds` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`levelId`, `sectionId`)
                    REFERENCES `level_sections`(`id`, `levelId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "INSERT INTO `level_requirements_new` " +
            "(`id`,`levelId`,`sectionId`,`text`,`type`,`order`,`isAdvanced`,`imageUrls`,`bookIds`) " +
            "SELECT `id`,`levelId`,`sectionId`,`text`,`type`,`order`,`isAdvanced`,`imageUrls`,`bookIds` " +
            "FROM `level_requirements`"
        )
        db.execSQL("DROP TABLE `level_requirements`")
        db.execSQL("ALTER TABLE `level_requirements_new` RENAME TO `level_requirements`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_level_requirements_levelId` ON `level_requirements` (`levelId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_level_requirements_sectionId` ON `level_requirements` (`sectionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_level_requirements_levelId_sectionId` ON `level_requirements` (`levelId`, `sectionId`)")
    }
}
