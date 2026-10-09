#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-stations F1 (infra) - 08.10.2026

- build.gradle.kts: zxing-android-embedded + play-services-location
- AndroidManifest.xml: CAMERA + ACCESS_COARSE/FINE_LOCATION
- NEW: QrStationEntity, StationVisitEntity
- NEW: QrStationDao, QrStationRepository
- NEW: Migration_6_7
- PATCH: AppDatabase (version 6->7, entities, dao)
- PATCH: Migrations.kt (ALL_MIGRATIONS + 6_7)
- PATCH: RepositoryModule (2 provides)

Запуск:
    python fix_qr_f1.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        die(path + ": файл уже существует.")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return

    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)

    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. build.gradle.kts
# ============================================================
patch("app/build.gradle.kts", [
    ("""    // ============ WorkManager ============
    implementation("androidx.work:work-runtime-ktx:$workManagerVersion")""",
     """    // ============ WorkManager ============
    implementation("androidx.work:work-runtime-ktx:$workManagerVersion")

    // ============ QR-станции (F) ============
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")"""),
], skip_if_marker="zxing-android-embedded")


# ============================================================
# 2. AndroidManifest.xml
# ============================================================
patch("app/src/main/AndroidManifest.xml", [
    ("""    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />""",
     """    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <!-- QR-станции (F) -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />"""),
], skip_if_marker="android.permission.CAMERA")


# ============================================================
# 3. QrStationEntity.kt
# ============================================================
QR_STATION = '''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * QR-станция — точка, которую может отсканировать teen.
 *
 * Типы станций:
 *   "entry"  - у входа в зал (отмечает посещение встречи)
 *   "quest"  - точка в городе (квест)
 *   "custom" - универсальная (библиотека, спортплощадка и т.п.)
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
    val type: String,                   // "entry" | "quest" | "custom"
    val rewardPoints: Int = 0,          // очки активности
    val rewardAchievementId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 100,
    val status: String = "active",      // "active" | "archived"
    val createdBy: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false,
)
'''

write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/QrStationEntity.kt", QR_STATION)


# ============================================================
# 4. StationVisitEntity.kt
# ============================================================
VISIT = '''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Посещение QR-станции.
 *
 * Сохраняется ДАЖЕ если гео-проверка не пройдена - с geoVerified = false.
 * Так модератор/директор увидит попытку и сможет разобраться.
 *
 * distanceMeters рассчитывается через haversine между
 * координатой станции и координатой пользователя на момент скана.
 */
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
'''

write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/StationVisitEntity.kt", VISIT)


# ============================================================
# 5. QrStationDao.kt
# ============================================================
DAO = '''package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrStationDao {

    // -------- Станции --------

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

    // -------- Посещения --------

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
}
'''

write_new("app/src/main/java/com/pathfinder/hub/data/local/dao/QrStationDao.kt", DAO)


# ============================================================
# 6. QrStationRepository.kt
# ============================================================
REPO = '''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.QrStationDao
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QR-станции и посещения.
 *
 * ВАЖНО: sync в Firestore пока НЕ настроен (нужен collection path в SyncWorker).
 * Все данные хранятся локально в Room. Синхронизация - отдельная задача.
 */
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

    // -------- Гео-проверка --------

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        /**
         * Расстояние между двумя координатами в метрах (haversine).
         */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return EARTH_RADIUS_M * c
        }

        /**
         * true, если расстояние от пользователя до станции <= radiusMeters.
         * Дополнительно учитывает точность GPS: если accuracy больше радиуса,
         * всё равно пропускаем (погрешность слишком велика, чтобы отклонить).
         */
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
'''

write_new("app/src/main/java/com/pathfinder/hub/data/repository/QrStationRepository.kt", REPO)


# ============================================================
# 7. Migration_6_7.kt
# ============================================================
MIGR = '''package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 6 -> 7.
 *
 * Добавляет две таблицы для QR-станций:
 *   - qr_stations     (станции, созданные директором)
 *   - station_visits  (сканирования teen-ов с гео-данными)
 *
 * Существующие таблицы не трогаем.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // -------- qr_stations --------
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

        // -------- station_visits --------
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
'''

write_new("app/src/main/java/com/pathfinder/hub/data/local/migration/Migration_6_7.kt", MIGR)


# ============================================================
# 8. Migrations.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/migration/Migrations.kt", [
    ("""val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
)""",
     """val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
)"""),
], skip_if_marker="MIGRATION_6_7")


# ============================================================
# 9. AppDatabase.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/AppDatabase.kt", [
    # import
    ("import com.pathfinder.hub.data.local.entity.parent.PWAConfigEntity",
     "import com.pathfinder.hub.data.local.entity.parent.PWAConfigEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.QrStationEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.StationVisitEntity"),

    # entities + version
    ("""        CachedNewspaperEntity::class
    ],
    version = 6,""",
     """        CachedNewspaperEntity::class,

        // QR-станции
        QrStationEntity::class,
        StationVisitEntity::class
    ],
    version = 7,"""),

    # abstract dao
    ("""    // Newspaper
    abstract fun newspaperDao(): NewspaperDao""",
     """    // Newspaper
    abstract fun newspaperDao(): NewspaperDao

    // QR-станции
    abstract fun qrStationDao(): QrStationDao"""),
])


# ============================================================
# 10. RepositoryModule.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/di/RepositoryModule.kt", [
    ("""    @Provides @Singleton fun provideNewspaperRepo(d: NewspaperDao) = NewspaperRepository(d)""",
     """    @Provides @Singleton fun provideNewspaperRepo(d: NewspaperDao) = NewspaperRepository(d)

    @Provides @Singleton
    fun provideQrStationDao(db: AppDatabase): QrStationDao = db.qrStationDao()

    @Provides @Singleton
    fun provideQrStationRepo(d: QrStationDao) = QrStationRepository(d)"""),
], skip_if_marker="provideQrStationRepo")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Что сделано (F1):")
print("  - gradle: zxing-android-embedded + play-services-location")
print("  - manifest: CAMERA + COARSE/FINE_LOCATION")
print("  - entities: QrStationEntity, StationVisitEntity")
print("  - dao: QrStationDao")
print("  - repo: QrStationRepository (+ haversine helper)")
print("  - migration: 6 -> 7")
print("  - AppDatabase: version 7, + 2 entities, + qrStationDao()")
print("  - RepositoryModule: + 2 provides")
print()
print("ВАЖНО: если сборка упадёт с 'duplicate binding for QrStationDao',")
print("значит DatabaseModule уже предоставляет все DAO — тогда убери")
print("provideQrStationDao из RepositoryModule (оставь только provideQrStationRepo).")
print()
print("UI сканера (F2) и управление станциями (F3) - следующими скриптами.")