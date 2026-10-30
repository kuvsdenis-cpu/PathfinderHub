package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE digital_uniforms ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE digital_uniforms ADD COLUMN syncedAt INTEGER")
    }
}

val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS custom_group_exclusions (" +
                    "groupId TEXT NOT NULL, " +
                    "userId TEXT NOT NULL, " +
                    "excludedAt INTEGER NOT NULL, " +
                    "excludedBy TEXT NOT NULL, " +
                    "reason TEXT NOT NULL DEFAULT 'left', " +
                    "pendingSync INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(groupId, userId))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_group_exclusions_groupId ON custom_group_exclusions(groupId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_group_exclusions_userId ON custom_group_exclusions(userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_group_exclusions_pendingSync ON custom_group_exclusions(pendingSync)")
    }
}

val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE custom_groups ADD COLUMN forcedMemberIdsJson TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS info_articles (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "categoryId TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "pdfUrl TEXT, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "pendingSync INTEGER NOT NULL DEFAULT 0, " +
                    "deletedAt INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_info_articles_categoryId ON info_articles(categoryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_info_articles_pendingSync ON info_articles(pendingSync)")
    }
}

val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS info_articles (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "categoryId TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "pdfUrl TEXT, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "pendingSync INTEGER NOT NULL DEFAULT 0, " +
                    "deletedAt INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_info_articles_categoryId ON info_articles(categoryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_info_articles_pendingSync ON info_articles(pendingSync)")
    }
}

/**
 * 54 -> 55: таблица для ответов Субботней школы.
 */
val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sabbath_answers` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `lessonId` TEXT NOT NULL,
                `blockId` TEXT NOT NULL,
                `answerText` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}


/**
 * 55 -> 56: пересоздаём level_requirements.
 *
 * Схема изменилась: добавлены bookIds, imageUrls, isAdvanced.
 * Данные не жалко — level_requirements сидовые, SeedDatabaseWorker
 * загрузит их заново из assets.
 */
val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `level_requirements`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `level_requirements` (
                `id` TEXT NOT NULL,
                `sectionId` TEXT NOT NULL,
                `levelId` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                `isAdvanced` INTEGER NOT NULL,
                `imageUrls` TEXT NOT NULL,
                `bookIds` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`levelId`, `sectionId`) REFERENCES `level_sections`(`levelId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_level_requirements_levelId` ON `level_requirements` (`levelId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_level_requirements_sectionId` ON `level_requirements` (`sectionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_level_requirements_levelId_sectionId` ON `level_requirements` (`levelId`, `sectionId`)")
    }
}

/**
 * 56 -> 57: soft delete для книг.
 * deletedAt: Date? (NULL — книга активна; не NULL — soft-deleted).
 */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN deletedAt INTEGER")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_33,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
    MIGRATION_38_39,
    MIGRATION_39_40,
    MIGRATION_40_41,
    MIGRATION_41_42,
    MIGRATION_42_43,
    MIGRATION_43_44,
    MIGRATION_44_45,
    MIGRATION_45_46,
    MIGRATION_46_47,
    MIGRATION_47_48,
    MIGRATION_48_49,
    MIGRATION_49_50,
    MIGRATION_50_51,
    MIGRATION_51_52,
    MIGRATION_52_53,
    MIGRATION_53_54,
    MIGRATION_54_55,
    MIGRATION_55_56,
    MIGRATION_56_57
)