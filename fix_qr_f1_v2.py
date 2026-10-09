#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-stations F1 v2 (infra) - 08.10.2026
Устойчивая версия: regex для манифеста, skip-if-marker везде.
Запуск:
    python fix_qr_f1_v2.py
"""

import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def read_text(path):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    return p, text, crlf


def write_text(p, text, crlf):
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))


def bak(p):
    b = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, b)
    print("[BAK]  " + b.name)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


# ============================================================
# 1. build.gradle.kts
# ============================================================
p, text, crlf = read_text("app/build.gradle.kts")
if "zxing-android-embedded" in text:
    print("[SKIP] build.gradle.kts")
else:
    old = """    // ============ WorkManager ============
    implementation("androidx.work:work-runtime-ktx:$workManagerVersion")"""
    new = """    // ============ WorkManager ============
    implementation("androidx.work:work-runtime-ktx:$workManagerVersion")

    // ============ QR-станции (F) ============
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")"""
    if old not in text:
        die("build.gradle: не нашёл блок WorkManager")
    bak(p)
    text = text.replace(old, new, 1)
    write_text(p, text, crlf)
    print("[OK]   build.gradle.kts")


# ============================================================
# 2. AndroidManifest.xml — regex
# ============================================================
p, text, crlf = read_text("app/src/main/AndroidManifest.xml")
if "android.permission.CAMERA" in text:
    print("[SKIP] AndroidManifest.xml")
else:
    insert = (
        "\n    <!-- QR-станции (F) -->\n"
        '    <uses-permission android:name="android.permission.CAMERA" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n'
        '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n'
    )
    # Ищем первую <uses-permission ... /> и вставляем ПОСЛЕ неё
    m = re.search(r'(<uses-permission[^>]*/>)', text)
    if not m:
        die("AndroidManifest.xml: не нашёл <uses-permission ... />")
    pos = m.end()
    text = text[:pos] + insert + text[pos:]
    bak(p)
    write_text(p, text, crlf)
    print("[OK]   AndroidManifest.xml")


# ============================================================
# 3. QrStationEntity.kt
# ============================================================
write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/QrStationEntity.kt",
'''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * QR-станция — точка, которую может отсканировать teen.
 *
 * Типы: "entry" | "quest" | "custom".
 */
@Entity(
    tableName = "qr_stations",
    indices = [Index("clubId"), Index("type"), Index("eventId")]
)
data class QrStationEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val eventId: String? = null,
    val name: String,
    val description: String = "",
    val type: String,
    val rewardPoints: Int = 0,
    val rewardAchievementId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 100,
    val status: String = "active",
    val createdBy: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false,
)
''')


# ============================================================
# 4. StationVisitEntity.kt
# ============================================================
write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/StationVisitEntity.kt",
'''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "station_visits",
    indices = [Index("stationId"), Index("userId"), Index("clubId"), Index("scannedAt")]
)
data class StationVisitEntity(
    @PrimaryKey val id: String,
    val stationId: String,
    val stationName: String,
    val stationType: String,
    val userId: String,
    val clubId: String,
    val scannedAt: Date,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val accuracyMeters: Float? = null,
    val distanceMeters: Double? = null,
    val geoVerified: Boolean,
    val rewardPoints: Int,
    val pendingSync: Boolean = false,
)
''')


# ============================================================
# 5. QrStationDao.kt
# ============================================================
write_new("app/src/main/java/com/pathfinder/hub/data/local/dao/QrStationDao.kt",
'''package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrStationDao {

    @Query("SELECT * FROM qr_stations WHERE clubId = :clubId AND status = 'active' ORDER BY name")
    fun observeActiveByClub(clubId: String): Flow<List<QrStationEntity>>

    @Query("SELECT * FROM qr_stations WHERE clubId = :clubId ORDER BY name")
    fun observeAllByClub(clubId: String): Flow<List<QrStationEntity>>

    @Query("SELECT * FROM qr_stations WHERE id = :id")
    suspend fun getById(id: String): QrStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStation(s: QrStationEntity)

    @Query("DELETE FROM qr_stations WHERE id = :id")
    suspend fun deleteStation(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisit(v: StationVisitEntity)

    @Query("SELECT * FROM station_visits WHERE stationId = :stationId AND userId = :userId ORDER BY scannedAt DESC")
    fun observeVisits(stationId: String, userId: String): Flow<List<StationVisitEntity>>

    @Query("SELECT * FROM station_visits WHERE userId = :userId ORDER BY scannedAt DESC LIMIT :limit")
    fun observeRecentVisits(userId: String, limit: Int = 50): Flow<List<StationVisitEntity>>

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND stationId = :stationId")
    suspend fun countVisits(userId: String, stationId: String): Int

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND geoVerified = 1")
    suspend fun countVerifiedVisits(userId: String): Int

    @Query("SELECT COUNT(*) FROM station_visits WHERE userId = :userId AND stationId = :stationId AND scannedAt >= :sinceMs")
    suspend fun countVisitsSince(userId: String, stationId: String, sinceMs: Long): Int
}
''')


# ============================================================
# 6. QrStationRepository.kt (сразу с countVisitsToday)
# ============================================================
write_new("app/src/main/java/com/pathfinder/hub/data/repository/QrStationRepository.kt",
'''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.QrStationDao
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrStationRepository @Inject constructor(
    private val dao: QrStationDao,
) {

    fun observeActiveStations(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeActiveByClub(clubId)

    fun observeAllStations(clubId: String): Flow<List<QrStationEntity>> =
        dao.observeAllByClub(clubId)

    suspend fun getStation(id: String): QrStationEntity? = dao.getById(id)

    suspend fun upsertStation(s: QrStationEntity) = dao.upsertStation(s)

    suspend fun deleteStation(id: String) = dao.deleteStation(id)

    suspend fun upsertVisit(v: StationVisitEntity) = dao.upsertVisit(v)

    fun observeVisits(stationId: String, userId: String): Flow<List<StationVisitEntity>> =
        dao.observeVisits(stationId, userId)

    fun observeRecentVisits(userId: String): Flow<List<StationVisitEntity>> =
        dao.observeRecentVisits(userId)

    suspend fun countVisits(userId: String, stationId: String): Int =
        dao.countVisits(userId, stationId)

    suspend fun countVerifiedVisits(userId: String): Int =
        dao.countVerifiedVisits(userId)

    suspend fun countVisitsToday(userId: String, stationId: String): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return dao.countVisitsSince(userId, stationId, cal.timeInMillis)
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return EARTH_RADIUS_M * c
        }

        fun isWithinRadius(
            stationLat: Double, stationLon: Double, radiusMeters: Int,
            userLat: Double, userLon: Double, accuracyMeters: Float?,
        ): Boolean {
            val d = distanceMeters(stationLat, stationLon, userLat, userLon)
            val effectiveRadius = radiusMeters + (accuracyMeters ?: 0f).toDouble()
            return d <= effectiveRadius
        }
    }
}
''')


# ============================================================
# 7. Migration_6_7.kt
# ============================================================
write_new("app/src/main/java/com/pathfinder/hub/data/local/migration/Migration_6_7.kt",
'''package com.pathfinder.hub.data.local.migration

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
''')


# ============================================================
# 8. Migrations.kt — +6_7
# ============================================================
p, text, crlf = read_text("app/src/main/java/com/pathfinder/hub/data/local/migration/Migrations.kt")
if "MIGRATION_6_7" in text:
    print("[SKIP] Migrations.kt")
else:
    old = """    MIGRATION_5_6,
)"""
    new = """    MIGRATION_5_6,
    MIGRATION_6_7,
)"""
    if old not in text:
        die("Migrations.kt: не нашёл 'MIGRATION_5_6,'")
    bak(p)
    text = text.replace(old, new, 1)
    write_text(p, text, crlf)
    print("[OK]   Migrations.kt")


# ============================================================
# 9. AppDatabase.kt
# ============================================================
p, text, crlf = read_text("app/src/main/java/com/pathfinder/hub/data/local/AppDatabase.kt")
changed = False

if "QrStationEntity" not in text:
    old = "import com.pathfinder.hub.data.local.entity.parent.PWAConfigEntity"
    new = (old + "\n"
           "import com.pathfinder.hub.data.local.entity.qr.QrStationEntity\n"
           "import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity")
    if old not in text:
        die("AppDatabase: нет import PWAConfigEntity")
    text = text.replace(old, new, 1)
    changed = True

if "version = 7" not in text:
    old = """        CachedNewspaperEntity::class
    ],
    version = 6,"""
    new = """        CachedNewspaperEntity::class,

        // QR-станции
        QrStationEntity::class,
        StationVisitEntity::class
    ],
    version = 7,"""
    if old not in text:
        die("AppDatabase: не нашёл 'CachedNewspaperEntity::class ... version = 6'")
    text = text.replace(old, new, 1)
    changed = True

if "qrStationDao" not in text:
    old = """    // Newspaper
    abstract fun newspaperDao(): NewspaperDao"""
    new = """    // Newspaper
    abstract fun newspaperDao(): NewspaperDao

    // QR-станции
    abstract fun qrStationDao(): QrStationDao"""
    if old not in text:
        die("AppDatabase: не нашёл 'newspaperDao'")
    text = text.replace(old, new, 1)
    changed = True

if changed:
    bak(p)
    write_text(p, text, crlf)
    print("[OK]   AppDatabase.kt")
else:
    print("[SKIP] AppDatabase.kt")


# ============================================================
# 10. RepositoryModule.kt
# ============================================================
p, text, crlf = read_text("app/src/main/java/com/pathfinder/hub/di/RepositoryModule.kt")
if "provideQrStationRepo" in text:
    print("[SKIP] RepositoryModule.kt")
else:
    old = """    @Provides @Singleton fun provideNewspaperRepo(d: NewspaperDao) = NewspaperRepository(d)"""
    new = """    @Provides @Singleton fun provideNewspaperRepo(d: NewspaperDao) = NewspaperRepository(d)

    @Provides @Singleton fun provideQrStationDao(db: AppDatabase): QrStationDao = db.qrStationDao()
    @Provides @Singleton fun provideQrStationRepo(d: QrStationDao) = QrStationRepository(d)"""
    if old not in text:
        die("RepositoryModule: не нашёл 'provideNewspaperRepo'")
    bak(p)
    text = text.replace(old, new, 1)
    write_text(p, text, crlf)
    print("[OK]   RepositoryModule.kt")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)