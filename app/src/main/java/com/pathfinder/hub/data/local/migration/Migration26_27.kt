package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 26 -> 27. B1: таблица user_clubs.
 *
 * Сидинг только из users.clubId — безопасно, без JSON1.
 * Досид из users.clubIds делает UserClubSeeder при старте приложения.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_clubs` (
                `userId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                PRIMARY KEY(`userId`, `clubId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_clubs_userId` ON `user_clubs` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_clubs_clubId` ON `user_clubs` (`clubId`)")

        db.execSQL(
            """
            INSERT OR IGNORE INTO `user_clubs`(`userId`, `clubId`)
            SELECT `id`, `clubId` FROM `users`
            WHERE `clubId` IS NOT NULL AND `clubId` != ''
            """.trimIndent()
        )
    }
}
