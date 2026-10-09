package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 6 -> 7. Добавляет таблицы QR-станций и посещений.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_stations` (
                `id` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `eventId` TEXT,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `rewardPoints` INTEGER NOT NULL,
                `rewardAchievementId` TEXT,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `radiusMeters` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `createdBy` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_stations_clubId` ON `qr_stations` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_stations_type` ON `qr_stations` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_stations_eventId` ON `qr_stations` (`eventId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `station_visits` (
                `id` TEXT NOT NULL,
                `stationId` TEXT NOT NULL,
                `stationName` TEXT NOT NULL,
                `stationType` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `scannedAt` INTEGER NOT NULL,
                `userLat` REAL,
                `userLon` REAL,
                `accuracyMeters` REAL,
                `distanceMeters` REAL,
                `geoVerified` INTEGER NOT NULL,
                `rewardPoints` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_visits_stationId` ON `station_visits` (`stationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_visits_userId` ON `station_visits` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_visits_clubId` ON `station_visits` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_visits_scannedAt` ON `station_visits` (`scannedAt`)")
    }
}
