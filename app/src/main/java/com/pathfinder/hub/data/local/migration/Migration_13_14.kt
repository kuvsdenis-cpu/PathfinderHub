package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 13 -> 14.
 * Cross-club: 3 таблицы.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cross_club_invites` (
                `id` TEXT NOT NULL,
                `hostClubId` TEXT NOT NULL,
                `inviteeClubId` TEXT NOT NULL,
                `targetType` TEXT NOT NULL,
                `targetId` TEXT NOT NULL,
                `createdBy` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `expiresAt` INTEGER,
                `status` TEXT NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invites_hostClubId` ON `cross_club_invites` (`hostClubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invites_inviteeClubId` ON `cross_club_invites` (`inviteeClubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invites_status` ON `cross_club_invites` (`status`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cross_club_invite_codes` (
                `id` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `hostClubId` TEXT NOT NULL,
                `targetType` TEXT NOT NULL,
                `targetId` TEXT NOT NULL,
                `createdBy` TEXT NOT NULL,
                `maxUses` INTEGER NOT NULL,
                `usedCount` INTEGER NOT NULL,
                `expiresAt` INTEGER,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cross_club_invite_codes_code` ON `cross_club_invite_codes` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invite_codes_hostClubId` ON `cross_club_invite_codes` (`hostClubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invite_codes_createdBy` ON `cross_club_invite_codes` (`createdBy`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invite_codes_status` ON `cross_club_invite_codes` (`status`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cross_club_invite_usages` (
                `id` TEXT NOT NULL,
                `inviteCodeId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `userClubId` TEXT NOT NULL,
                `usedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invite_usages_inviteCodeId` ON `cross_club_invite_usages` (`inviteCodeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invite_usages_userId` ON `cross_club_invite_usages` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cross_club_invite_usages_userClubId` ON `cross_club_invite_usages` (`userClubId`)")
    }
}
