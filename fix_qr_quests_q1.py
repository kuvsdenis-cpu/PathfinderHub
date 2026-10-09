#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-quests Q1 (model) - 09.10.2026

- QrQuestEntity, QrQuestStationEntity, QrQuestProgressEntity
- QrQuestDao, QrQuestRepository
- Migration_9_10, AppDatabase version 10
- firestore.rules: qrQuests, qrQuestProgress

Запуск:
    python fix_qr_quests_q1.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
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
# 1. QrQuestEntity.kt
# ============================================================
QUEST = '''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * QR-квест — серия станций, которые teen проходит по порядку.
 *
 * Тип: только "linear" (строго по порядку) на данной итерации.
 *
 * dateStart/dateEnd nullable:
 *   - dateEnd = null  → бессрочный
 *   - dateEnd != null → ограничен по дате
 */
@Entity(
    tableName = "qr_quests",
    indices = [Index("clubId"), Index("status")]
)
data class QrQuestEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val title: String,
    val description: String = "",
    val type: String = "linear",
    val rewardPoints: Int = 0,
    val rewardAchievementId: String? = null,
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val status: String = "active",   // "active" | "archived"
    val createdBy: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false,
)
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/QrQuestEntity.kt", QUEST)


# ============================================================
# 2. QrQuestStationEntity.kt
# ============================================================
QUEST_STATION = '''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Связь квест ↔ станция с порядковым номером.
 *
 * Составной PK: одна станция может быть в нескольких квестах,
 * в каждом — со своим stepIndex.
 */
@Entity(
    tableName = "qr_quest_stations",
    primaryKeys = ["questId", "stationId"],
    indices = [Index("questId"), Index("stationId")]
)
data class QrQuestStationEntity(
    val questId: String,
    val stationId: String,
    val stepIndex: Int,   // 1, 2, 3, ...
)
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/QrQuestStationEntity.kt", QUEST_STATION)


# ============================================================
# 3. QrQuestProgressEntity.kt
# ============================================================
PROGRESS = '''package com.pathfinder.hub.data.local.entity.qr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Прогресс teen по квесту.
 *
 * lastCompletedStepIndex — сколько шагов пройдено по порядку (N).
 * completedStepIndices — все отмеченные шаги (для истории).
 * Если lastCompletedStepIndex == всего шагов → completedAt != null.
 */
@Entity(
    tableName = "qr_quest_progress",
    primaryKeys = ["questId", "userId"],
    indices = [Index("questId"), Index("userId"), Index("clubId")]
)
data class QrQuestProgressEntity(
    val questId: String,
    val userId: String,
    val clubId: String,
    val lastCompletedStepIndex: Int = 0,
    val completedStepIndices: List<Int> = emptyList(),
    val startedAt: Date? = null,
    val completedAt: Date? = null,
    val rewardClaimed: Boolean = false,
    val pendingSync: Boolean = false,
)
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/entity/qr/QrQuestProgressEntity.kt", PROGRESS)


# ============================================================
# 4. QrQuestDao.kt
# ============================================================
DAO = '''package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrQuestDao {

    // -------- Квесты --------

    @Query("SELECT * FROM qr_quests WHERE clubId = :clubId AND status = 'active' ORDER BY createdAt DESC")
    fun observeActiveByClub(clubId: String): Flow<List<QrQuestEntity>>

    @Query("SELECT * FROM qr_quests WHERE clubId = :clubId ORDER BY createdAt DESC")
    fun observeAllByClub(clubId: String): Flow<List<QrQuestEntity>>

    @Query("SELECT * FROM qr_quests WHERE id = :id")
    suspend fun getById(id: String): QrQuestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuest(q: QrQuestEntity)

    @Query("DELETE FROM qr_quests WHERE id = :id")
    suspend fun deleteQuest(id: String)

    // -------- Связи квест-станция --------

    @Query("SELECT * FROM qr_quest_stations WHERE questId = :questId ORDER BY stepIndex")
    fun observeQuestStations(questId: String): Flow<List<QrQuestStationEntity>>

    @Query("SELECT * FROM qr_quest_stations WHERE questId = :questId ORDER BY stepIndex")
    suspend fun getQuestStations(questId: String): List<QrQuestStationEntity>

    /**
     * Найти все квесты, в которые входит станция.
     * Нужно при сканировании: одна станция = может быть в нескольких квестах.
     */
    @Query("SELECT * FROM qr_quest_stations WHERE stationId = :stationId ORDER BY stepIndex")
    suspend fun getQuestsForStation(stationId: String): List<QrQuestStationEntity>

    @Query("SELECT COUNT(*) FROM qr_quest_stations WHERE questId = :questId")
    suspend fun getStepCount(questId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestStation(s: QrQuestStationEntity)

    @Query("DELETE FROM qr_quest_stations WHERE questId = :questId")
    suspend fun clearQuestStations(questId: String)

    @Query("DELETE FROM qr_quest_stations WHERE questId = :questId AND stationId = :stationId")
    suspend fun deleteQuestStation(questId: String, stationId: String)

    // -------- Прогресс --------

    @Query("SELECT * FROM qr_quest_progress WHERE questId = :questId AND userId = :userId LIMIT 1")
    fun observeProgress(questId: String, userId: String): Flow<QrQuestProgressEntity?>

    @Query("SELECT * FROM qr_quest_progress WHERE questId = :questId AND userId = :userId LIMIT 1")
    suspend fun getProgress(questId: String, userId: String): QrQuestProgressEntity?

    @Query("SELECT * FROM qr_quest_progress WHERE userId = :userId")
    fun observeAllProgressByUser(userId: String): Flow<List<QrQuestProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: QrQuestProgressEntity)
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/dao/QrQuestDao.kt", DAO)


# ============================================================
# 5. QrQuestRepository.kt
# ============================================================
REPO = '''package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.QrQuestDao
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity
import com.pathfinder.hub.data.local.entity.qr.QrQuestStationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QR-квесты: логика чтения/записи.
 *
 * Firestore-синк пока НЕ реализован (нужен resolveCollectionPath
 * для qr_quest / qr_quest_progress в SyncWorker). Всё локально в Room.
 */
@Singleton
class QrQuestRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: QrQuestDao,
) {

    // ---------- Квесты ----------

    fun observeActiveQuests(clubId: String): Flow<List<QrQuestEntity>> =
        dao.observeActiveByClub(clubId)

    fun observeAllQuests(clubId: String): Flow<List<QrQuestEntity>> =
        dao.observeAllByClub(clubId)

    suspend fun getQuest(id: String): QrQuestEntity? = dao.getById(id)

    suspend fun upsertQuest(q: QrQuestEntity) {
        db.withTransaction { dao.upsertQuest(q) }
    }

    suspend fun deleteQuest(id: String) {
        db.withTransaction { dao.deleteQuest(id) }
    }

    // ---------- Связи ----------

    fun observeQuestStations(questId: String): Flow<List<QrQuestStationEntity>> =
        dao.observeQuestStations(questId)

    suspend fun getQuestStations(questId: String): List<QrQuestStationEntity> =
        dao.getQuestStations(questId)

    suspend fun getQuestsForStation(stationId: String): List<QrQuestStationEntity> =
        dao.getQuestsForStation(stationId)

    suspend fun getStepCount(questId: String): Int = dao.getStepCount(questId)

    suspend fun upsertQuestStation(s: QrQuestStationEntity) {
        db.withTransaction { dao.upsertQuestStation(s) }
    }

    suspend fun clearQuestStations(questId: String) {
        db.withTransaction { dao.clearQuestStations(questId) }
    }

    suspend fun deleteQuestStation(questId: String, stationId: String) {
        db.withTransaction { dao.deleteQuestStation(questId, stationId) }
    }

    /**
     * Установить полный список станций квеста.
     * Пересчитывает stepIndex по порядку переданного списка.
     */
    suspend fun setQuestStations(questId: String, stationIds: List<String>) {
        db.withTransaction {
            dao.clearQuestStations(questId)
            stationIds.forEachIndexed { idx, sid ->
                dao.upsertQuestStation(
                    QrQuestStationEntity(
                        questId = questId,
                        stationId = sid,
                        stepIndex = idx + 1,
                    )
                )
            }
        }
    }

    // ---------- Прогресс ----------

    fun observeProgress(questId: String, userId: String): Flow<QrQuestProgressEntity?> =
        dao.observeProgress(questId, userId)

    suspend fun getProgress(questId: String, userId: String): QrQuestProgressEntity? =
        dao.getProgress(questId, userId)

    fun observeAllProgressByUser(userId: String): Flow<List<QrQuestProgressEntity>> =
        dao.observeAllProgressByUser(userId)

    suspend fun upsertProgress(p: QrQuestProgressEntity) {
        db.withTransaction { dao.upsertProgress(p) }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/repository/QrQuestRepository.kt", REPO)


# ============================================================
# 6. Migration_9_10.kt
# ============================================================
MIGR = '''package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 9 -> 10.
 *
 * Добавляет таблицы QR-квестов:
 *   - qr_quests           (сами квесты)
 *   - qr_quest_stations   (связи квест-станция с порядком)
 *   - qr_quest_progress   (прогресс teen по квесту)
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_quests` (
                `id` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `rewardPoints` INTEGER NOT NULL,
                `rewardAchievementId` TEXT,
                `dateStart` INTEGER,
                `dateEnd` INTEGER,
                `status` TEXT NOT NULL,
                `createdBy` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quests_clubId` ON `qr_quests` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quests_status` ON `qr_quests` (`status`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_quest_stations` (
                `questId` TEXT NOT NULL,
                `stationId` TEXT NOT NULL,
                `stepIndex` INTEGER NOT NULL,
                PRIMARY KEY(`questId`, `stationId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_stations_questId` ON `qr_quest_stations` (`questId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_stations_stationId` ON `qr_quest_stations` (`stationId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `qr_quest_progress` (
                `questId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `lastCompletedStepIndex` INTEGER NOT NULL,
                `completedStepIndices` TEXT NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `rewardClaimed` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`questId`, `userId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_progress_questId` ON `qr_quest_progress` (`questId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_progress_userId` ON `qr_quest_progress` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_qr_quest_progress_clubId` ON `qr_quest_progress` (`clubId`)")
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/migration/Migration_9_10.kt", MIGR)


# ============================================================
# 7. Migrations.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/migration/Migrations.kt", [
    ("""    MIGRATION_8_9,
)""",
     """    MIGRATION_8_9,
    MIGRATION_9_10,
)"""),
], skip_if_marker="MIGRATION_9_10")


# ============================================================
# 8. AppDatabase.kt — version 10 + entities + dao
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/AppDatabase.kt", [
    # import
    ("import com.pathfinder.hub.data.local.entity.qr.QrStationEntity",
     "import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.QrQuestProgressEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.QrQuestStationEntity\n"
     "import com.pathfinder.hub.data.local.entity.qr.QrStationEntity"),

    # entities list + version
    ("""        // QR-станции
        QrStationEntity::class,
        StationVisitEntity::class
    ],
    version = 9,""",
     """        // QR-станции
        QrStationEntity::class,
        StationVisitEntity::class,

        // QR-квесты
        QrQuestEntity::class,
        QrQuestStationEntity::class,
        QrQuestProgressEntity::class
    ],
    version = 10,"""),

    # dao
    ("""    // QR-станции
    abstract fun qrStationDao(): QrStationDao""",
     """    // QR-станции
    abstract fun qrStationDao(): QrStationDao

    // QR-квесты
    abstract fun qrQuestDao(): QrQuestDao"""),
])


# ============================================================
# 9. RepositoryModule.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/di/RepositoryModule.kt", [
    ("""    @Provides @Singleton
    fun provideQrStationRepo(
        db: AppDatabase,
        d: QrStationDao,
        firestore: com.pathfinder.hub.data.remote.FirestoreQrDataSource,
        syncQueue: SyncQueueManager,
    ) = QrStationRepository(db, d, firestore, syncQueue)""",
     """    @Provides @Singleton
    fun provideQrStationRepo(
        db: AppDatabase,
        d: QrStationDao,
        firestore: com.pathfinder.hub.data.remote.FirestoreQrDataSource,
        syncQueue: SyncQueueManager,
    ) = QrStationRepository(db, d, firestore, syncQueue)

    @Provides @Singleton
    fun provideQrQuestDao(db: AppDatabase): QrQuestDao = db.qrQuestDao()

    @Provides @Singleton
    fun provideQrQuestRepo(db: AppDatabase, d: QrQuestDao) = QrQuestRepository(db, d)"""),
], skip_if_marker="provideQrQuestRepo")


# ============================================================
# 10. firestore.rules
# ============================================================
patch("firestore.rules", [
    ("""      match /qrStations/{stationId} {""",
     """      match /qrQuests/{questId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (hasPerm("qr.create") && isOwnClub(clubId));
        allow delete: if isSignedIn() && (hasPerm("qr.delete") && isOwnClub(clubId));
      }

      match /qrQuestStations/{linkId} {
        allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
        allow create, update: if isSignedIn() && (hasPerm("qr.create") && isOwnClub(clubId));
        allow delete: if isSignedIn() && (hasPerm("qr.delete") && isOwnClub(clubId));
      }

      match /qrQuestProgress/{progressId} {
        allow read: if isSignedIn() && (
          resource.data.userId == uid()
          || (isClubStaff() && isOwnClub(clubId))
          || isConferenceAdmin()
        );
        allow create, update: if isSignedIn()
          && request.resource.data.userId == uid()
          && request.resource.data.clubId == clubId;
        allow delete: if false;
      }

      match /qrStations/{stationId} {"""),
], skip_if_marker="match /qrQuests/")


print()
print("=" * 60)
print("Q1 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("  firebase deploy --only firestore:rules")
print()
print("Что сделано:")
print("  - QrQuestEntity: квест (title, dateStart?, dateEnd?)")
print("  - QrQuestStationEntity: связь квест<->станция со stepIndex")
print("  - QrQuestProgressEntity: прогресс teen (lastStep, completedAt)")
print("  - QrQuestDao: CRUD + getQuestsForStation")
print("  - QrQuestRepository: обёртка + setQuestStations(пересчёт stepIndex)")
print("  - Migration_9_10: 3 таблицы")
print("  - AppDatabase: version 9 -> 10, +3 entities, +qrQuestDao()")
print("  - RepositoryModule: +provideQrQuestDao, +provideQrQuestRepo")
print("  - firestore.rules: qrQuests, qrQuestStations, qrQuestProgress")
print()
print("Проверка:")
print("  - Сборка прошла, миграция не упала")
print("  - Существующие QR-станции работают как раньше")
print()
print("Откат: .bak.<timestamp>")