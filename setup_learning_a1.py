#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — Сессия A1.
Создаёт 7 файлов + патчит AppDatabase.kt и Migrations.kt.

Запуск из корня проекта (где settings.gradle.kts):
    python3 setup_learning_a1.py
"""

import shutil
import sys
from pathlib import Path


# ============================================================
# ПОИСК КОРНЯ ПРОЕКТА
# ============================================================
def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта (settings.gradle.kts).")
    print("   Запусти скрипт из корня проекта.")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"

if not PKG.exists():
    print(f"❌ Не найден пакет: {PKG}")
    sys.exit(1)


# ============================================================
# СОДЕРЖИМОЕ НОВЫХ ФАЙЛОВ
# ============================================================
FILES = {}

# ---------- 1. RequirementMaterialEntity ----------
FILES["data/local/entity/learning/RequirementMaterialEntity.kt"] = '''package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Сопутствующий материал требования ступени/специализации.
 *
 * Источник:
 *   - JSON (levels.json / honors.json) -> clubId = "", createdBy = null
 *   - Правка директора (Firestore)     -> clubId = <clubId>, createdBy = <uid>
 *
 * Флаг flaggedForEdit ставит наставник клуба: "материал надо поправить".
 * Редактирует только директор.
 */
@Entity(
    tableName = "requirement_materials",
    indices = [
        Index("requirementId"),
        Index("clubId"),
        Index("flaggedForEdit"),
        Index("requirementId", "clubId")
    ]
)
data class RequirementMaterialEntity(
    @PrimaryKey val id: String,
    val requirementId: String,
    val clubId: String,
    val type: String,
    val title: String,
    val content: String,
    val orderIndex: Int,
    val flaggedForEdit: Boolean = false,
    val flaggedBy: String? = null,
    val flaggedAt: Date? = null,
    val flagComment: String? = null,
    val createdBy: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false
)
'''

# ---------- 2. RequirementTaskEntity ----------
FILES["data/local/entity/learning/RequirementTaskEntity.kt"] = '''package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Проверочное задание требования.
 *
 * Типы:
 *   Автопроверка (autoCheck = true):
 *     "single_choice" | "multi_choice" | "number" | "cipher" | "crossword"
 *   Ручная проверка (autoCheck = false):
 *     "short_text" | "long_text" | "file" | "date" | "checkbox"
 *
 * Флаг flaggedForEdit ставит наставник клуба. Редактирует только директор.
 */
@Entity(
    tableName = "requirement_tasks",
    indices = [
        Index("requirementId"),
        Index("clubId"),
        Index("flaggedForEdit"),
        Index("requirementId", "clubId")
    ]
)
data class RequirementTaskEntity(
    @PrimaryKey val id: String,
    val requirementId: String,
    val clubId: String,
    val type: String,
    val title: String,
    val description: String? = null,
    val options: List<String> = emptyList(),
    val correctAnswer: String? = null,
    val autoCheck: Boolean = false,
    val isRequired: Boolean = true,
    val orderIndex: Int,
    val flaggedForEdit: Boolean = false,
    val flaggedBy: String? = null,
    val flaggedAt: Date? = null,
    val flagComment: String? = null,
    val createdBy: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false
) {
    companion object {
        private val AUTO_CHECK_TYPES = setOf(
            "single_choice", "multi_choice", "number", "cipher", "crossword"
        )

        fun isAutoCheckType(type: String): Boolean = type in AUTO_CHECK_TYPES
    }
}
'''

# ---------- 3. RequirementTaskAnswerEntity ----------
FILES["data/local/entity/learning/RequirementTaskAnswerEntity.kt"] = '''package com.pathfinder.hub.data.local.entity.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Ответ следопыта на проверочное задание.
 *
 * Статусы:
 *   "draft"         - начал, не отправил
 *   "submitted"     - отправлено на ручную проверку
 *   "auto_approved" - автопроверка прошла
 *   "approved"      - наставник/директор одобрил
 *   "rejected"      - наставник/директор отклонил (reviewComment обязателен)
 */
@Entity(
    tableName = "requirement_task_answers",
    indices = [
        Index("taskId"),
        Index("requirementId"),
        Index("userId"),
        Index("clubId"),
        Index("status"),
        Index("taskId", "userId", unique = true)
    ]
)
data class RequirementTaskAnswerEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val requirementId: String,
    val userId: String,
    val clubId: String,
    val answer: String,
    val attachments: List<String> = emptyList(),
    val status: String,
    val autoChecked: Boolean = false,
    val submittedAt: Date? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Date? = null,
    val reviewComment: String? = null,
    val createdAt: Date,
    val updatedAt: Date,
    val pendingSync: Boolean = false
) {
    companion object {
        const val STATUS_DRAFT = "draft"
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_AUTO_APPROVED = "auto_approved"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"

        val DONE_STATUSES = setOf(STATUS_APPROVED, STATUS_AUTO_APPROVED)
    }
}
'''

# ---------- 4. Migration_5_6 ----------
FILES["data/local/migration/Migration_5_6.kt"] = '''package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 5 -> 6.
 *
 * Добавляет три таблицы:
 *   - requirement_materials       (сопутствующие материалы)
 *   - requirement_tasks           (проверочные задания)
 *   - requirement_task_answers    (ответы следопытов)
 *
 * Существующие таблицы не трогаем.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // -------- requirement_materials --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `requirement_materials` (
                `id` TEXT NOT NULL,
                `requirementId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `flaggedForEdit` INTEGER NOT NULL DEFAULT 0,
                `flaggedBy` TEXT,
                `flaggedAt` INTEGER,
                `flagComment` TEXT,
                `createdBy` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_requirementId` ON `requirement_materials` (`requirementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_clubId` ON `requirement_materials` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_flaggedForEdit` ON `requirement_materials` (`flaggedForEdit`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_materials_requirementId_clubId` ON `requirement_materials` (`requirementId`, `clubId`)")

        // -------- requirement_tasks --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `requirement_tasks` (
                `id` TEXT NOT NULL,
                `requirementId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `options` TEXT NOT NULL DEFAULT '',
                `correctAnswer` TEXT,
                `autoCheck` INTEGER NOT NULL DEFAULT 0,
                `isRequired` INTEGER NOT NULL DEFAULT 1,
                `orderIndex` INTEGER NOT NULL,
                `flaggedForEdit` INTEGER NOT NULL DEFAULT 0,
                `flaggedBy` TEXT,
                `flaggedAt` INTEGER,
                `flagComment` TEXT,
                `createdBy` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_requirementId` ON `requirement_tasks` (`requirementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_clubId` ON `requirement_tasks` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_flaggedForEdit` ON `requirement_tasks` (`flaggedForEdit`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_tasks_requirementId_clubId` ON `requirement_tasks` (`requirementId`, `clubId`)")

        // -------- requirement_task_answers --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `requirement_task_answers` (
                `id` TEXT NOT NULL,
                `taskId` TEXT NOT NULL,
                `requirementId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `clubId` TEXT NOT NULL,
                `answer` TEXT NOT NULL,
                `attachments` TEXT NOT NULL DEFAULT '',
                `status` TEXT NOT NULL,
                `autoChecked` INTEGER NOT NULL DEFAULT 0,
                `submittedAt` INTEGER,
                `reviewedBy` TEXT,
                `reviewedAt` INTEGER,
                `reviewComment` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `pendingSync` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_taskId` ON `requirement_task_answers` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_requirementId` ON `requirement_task_answers` (`requirementId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_userId` ON `requirement_task_answers` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_clubId` ON `requirement_task_answers` (`clubId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirement_task_answers_status` ON `requirement_task_answers` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_requirement_task_answers_taskId_userId` ON `requirement_task_answers` (`taskId`, `userId`)")
    }
}
'''

# ---------- 5. RequirementMaterialDao ----------
FILES["data/local/dao/RequirementMaterialDao.kt"] = '''package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequirementMaterialDao {

    @Query(
        """
        SELECT * FROM requirement_materials
        WHERE requirementId = :requirementId
          AND (clubId = :clubId OR clubId = '')
        ORDER BY orderIndex ASC
        """
    )
    fun observeForRequirement(requirementId: String, clubId: String): Flow<List<RequirementMaterialEntity>>

    @Query("SELECT * FROM requirement_materials WHERE requirementId = :requirementId")
    suspend fun getByRequirement(requirementId: String): List<RequirementMaterialEntity>

    @Query("SELECT * FROM requirement_materials WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RequirementMaterialEntity?

    @Query("SELECT * FROM requirement_materials WHERE flaggedForEdit = 1 ORDER BY flaggedAt DESC")
    fun observeFlagged(): Flow<List<RequirementMaterialEntity>>

    @Query("SELECT * FROM requirement_materials WHERE requirementId = :requirementId AND clubId = :clubId ORDER BY orderIndex ASC")
    fun observeClubOverrides(requirementId: String, clubId: String): Flow<List<RequirementMaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(material: RequirementMaterialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(materials: List<RequirementMaterialEntity>)

    @Query("DELETE FROM requirement_materials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM requirement_materials WHERE requirementId = :requirementId AND clubId = :clubId")
    suspend fun deleteByRequirementAndClub(requirementId: String, clubId: String)

    @Query("""
        UPDATE requirement_materials
        SET flaggedForEdit = 1, flaggedBy = :by, flaggedAt = :at,
            flagComment = :comment, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun flag(id: String, by: String, at: Long, comment: String?)

    @Query("""
        UPDATE requirement_materials
        SET flaggedForEdit = 0, flaggedBy = NULL, flaggedAt = NULL,
            flagComment = NULL, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun unflag(id: String, at: Long)

    @Query("SELECT * FROM requirement_materials WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<RequirementMaterialEntity>

    @Query("UPDATE requirement_materials SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPendingSync(id: String)
}
'''

# ---------- 6. RequirementTaskDao ----------
FILES["data/local/dao/RequirementTaskDao.kt"] = '''package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequirementTaskDao {

    @Query("""
        SELECT * FROM requirement_tasks
        WHERE requirementId = :requirementId
          AND (clubId = :clubId OR clubId = '')
        ORDER BY orderIndex ASC
    """)
    fun observeForRequirement(requirementId: String, clubId: String): Flow<List<RequirementTaskEntity>>

    @Query("SELECT * FROM requirement_tasks WHERE requirementId = :requirementId")
    suspend fun getByRequirement(requirementId: String): List<RequirementTaskEntity>

    @Query("SELECT * FROM requirement_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RequirementTaskEntity?

    @Query("""
        SELECT * FROM requirement_tasks
        WHERE requirementId = :requirementId
          AND (clubId = :clubId OR clubId = '')
          AND isRequired = 1
    """)
    suspend fun getRequiredForRequirement(requirementId: String, clubId: String): List<RequirementTaskEntity>

    @Query("SELECT * FROM requirement_tasks WHERE flaggedForEdit = 1 ORDER BY flaggedAt DESC")
    fun observeFlagged(): Flow<List<RequirementTaskEntity>>

    @Query("SELECT * FROM requirement_tasks WHERE requirementId = :requirementId AND clubId = :clubId ORDER BY orderIndex ASC")
    fun observeClubOverrides(requirementId: String, clubId: String): Flow<List<RequirementTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: RequirementTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<RequirementTaskEntity>)

    @Query("DELETE FROM requirement_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM requirement_tasks WHERE requirementId = :requirementId AND clubId = :clubId")
    suspend fun deleteByRequirementAndClub(requirementId: String, clubId: String)

    @Query("""
        UPDATE requirement_tasks
        SET flaggedForEdit = 1, flaggedBy = :by, flaggedAt = :at,
            flagComment = :comment, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun flag(id: String, by: String, at: Long, comment: String?)

    @Query("""
        UPDATE requirement_tasks
        SET flaggedForEdit = 0, flaggedBy = NULL, flaggedAt = NULL,
            flagComment = NULL, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun unflag(id: String, at: Long)

    @Query("SELECT * FROM requirement_tasks WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<RequirementTaskEntity>

    @Query("UPDATE requirement_tasks SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPendingSync(id: String)
}
'''

# ---------- 7. RequirementTaskAnswerDao ----------
FILES["data/local/dao/RequirementTaskAnswerDao.kt"] = '''package com.pathfinder.hub.data.local.dao

import androidx.room.*
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequirementTaskAnswerDao {

    @Query("SELECT * FROM requirement_task_answers WHERE requirementId = :requirementId AND userId = :userId")
    fun observeForUserAndRequirement(requirementId: String, userId: String): Flow<List<RequirementTaskAnswerEntity>>

    @Query("SELECT * FROM requirement_task_answers WHERE taskId = :taskId AND userId = :userId LIMIT 1")
    suspend fun getByTaskAndUser(taskId: String, userId: String): RequirementTaskAnswerEntity?

    @Query("SELECT * FROM requirement_task_answers WHERE taskId = :taskId AND userId = :userId LIMIT 1")
    fun observeByTaskAndUser(taskId: String, userId: String): Flow<RequirementTaskAnswerEntity?>

    @Query("SELECT * FROM requirement_task_answers WHERE clubId = :clubId AND status = 'submitted' ORDER BY submittedAt ASC")
    fun observeSubmittedForClub(clubId: String): Flow<List<RequirementTaskAnswerEntity>>

    @Query("""
        SELECT * FROM requirement_task_answers
        WHERE requirementId = :requirementId AND userId = :userId
          AND status IN ('approved', 'auto_approved')
    """)
    suspend fun getApprovedForRequirement(requirementId: String, userId: String): List<RequirementTaskAnswerEntity>

    @Query("""
        SELECT COUNT(*) FROM requirement_task_answers
        WHERE requirementId = :requirementId AND userId = :userId
          AND status IN ('approved', 'auto_approved')
    """)
    suspend fun countApprovedForRequirement(requirementId: String, userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(answer: RequirementTaskAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(answers: List<RequirementTaskAnswerEntity>)

    @Query("DELETE FROM requirement_task_answers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""
        UPDATE requirement_task_answers
        SET answer = :answer, attachments = :attachments, status = :status,
            submittedAt = :submittedAt, updatedAt = :updatedAt, pendingSync = 1
        WHERE id = :id
    """)
    suspend fun updateAnswer(
        id: String, answer: String, attachments: String, status: String,
        submittedAt: Long?, updatedAt: Long
    )

    @Query("""
        UPDATE requirement_task_answers
        SET status = :status, reviewedBy = :by, reviewedAt = :at,
            reviewComment = :comment, pendingSync = 1, updatedAt = :at
        WHERE id = :id
    """)
    suspend fun review(id: String, status: String, by: String, at: Long, comment: String?)

    @Query("SELECT * FROM requirement_task_answers WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<RequirementTaskAnswerEntity>

    @Query("UPDATE requirement_task_answers SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPendingSync(id: String)
}
'''


# ============================================================
# ЗАПИСЬ НОВЫХ ФАЙЛОВ
# ============================================================
def write_files() -> None:
    print(f"📁 Корень проекта: {ROOT}")
    print(f"📁 Пакет:          {PKG}\n")

    created, replaced = 0, 0

    for rel, content in FILES.items():
        target = PKG / rel
        target.parent.mkdir(parents=True, exist_ok=True)

        if target.exists():
            backup = target.with_suffix(target.suffix + ".bak")
            shutil.copy2(target, backup)
            print(f"⚠️  Перезаписываю {rel} (бэкап: {backup.name})")
            replaced += 1

        target.write_text(content, encoding="utf-8")
        print(f"✅ {rel}")
        created += 1

    print(f"\n📦 Создано: {created}, бэкапов: {replaced}")


# ============================================================
# ПАТЧ AppDatabase.kt
# ============================================================
def patch_app_database() -> bool:
    print("\n🔧 Патчу AppDatabase.kt...")

    db_file = PKG / "data/local/AppDatabase.kt"
    if not db_file.exists():
        print(f"❌ Не найден {db_file}")
        return False

    text = db_file.read_text(encoding="utf-8")
    original = text

    # ---------- 1. Импорты entity ----------
    import_anchor = "import com.pathfinder.hub.data.local.entity.learning.LeaderChecklistEntity"
    if import_anchor not in text:
        print(f"❌ Не найден якорь импорта: {import_anchor}")
        return False

    if "RequirementMaterialEntity" not in text:
        new_imports = (
            "\nimport com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity"
            "\nimport com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity"
            "\nimport com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity"
        )
        text = text.replace(import_anchor, import_anchor + new_imports, 1)

    # ---------- 2. version = 5 → 6 ----------
    if "version = 5," in text:
        text = text.replace("version = 5,", "version = 6,", 1)
    elif "version = 6," not in text:
        print("❌ Не найден 'version = 5,' в @Database")
        return False

    # ---------- 3. entity в @Database ----------
    entity_anchor = "        LeaderChecklistEntity::class,"
    if entity_anchor not in text:
        print(f"❌ Не найден якорь entity: {entity_anchor}")
        return False

    if "RequirementMaterialEntity::class" not in text:
        new_entities = (
            "\n        RequirementMaterialEntity::class,"
            "\n        RequirementTaskEntity::class,"
            "\n        RequirementTaskAnswerEntity::class,"
        )
        text = text.replace(entity_anchor, entity_anchor + new_entities, 1)

    # ---------- 4. DAO ----------
    dao_anchor = "    // Gamification\n    abstract fun achievementDao(): AchievementDao"
    if dao_anchor not in text:
        print(f"❌ Не найден якорь DAO: {dao_anchor}")
        return False

    if "requirementMaterialDao" not in text:
        new_daos = (
            "    // Learning — материалы и задания\n"
            "    abstract fun requirementMaterialDao(): RequirementMaterialDao\n"
            "    abstract fun requirementTaskDao(): RequirementTaskDao\n"
            "    abstract fun requirementTaskAnswerDao(): RequirementTaskAnswerDao\n\n"
        )
        text = text.replace(dao_anchor, new_daos + dao_anchor, 1)

    # ---------- 5. Импорт DAO (если нет wildcard dao.*) ----------
    if "import com.pathfinder.hub.data.local.dao.*" not in text:
        if "import com.pathfinder.hub.data.local.dao.RequirementMaterialDao" not in text:
            dao_import_anchor = "import com.pathfinder.hub.data.local.dao.LevelDao"
            if dao_import_anchor in text:
                text = text.replace(
                    dao_import_anchor,
                    "import com.pathfinder.hub.data.local.dao.RequirementMaterialDao\n"
                    "import com.pathfinder.hub.data.local.dao.RequirementTaskDao\n"
                    "import com.pathfinder.hub.data.local.dao.RequirementTaskAnswerDao\n"
                    + dao_import_anchor,
                    1,
                )

    if text == original:
        print("⚠️  AppDatabase.kt уже пропатчен — ничего не меняю")
        return True

    backup = db_file.with_suffix(".kt.bak")
    shutil.copy2(db_file, backup)
    db_file.write_text(text, encoding="utf-8")
    print(f"✅ AppDatabase.kt пропатчен (бэкап: {backup.name})")
    return True


# ============================================================
# ПАТЧ Migrations.kt
# ============================================================
def patch_migrations() -> bool:
    print("\n🔧 Патчу Migrations.kt...")

    mig_file = PKG / "data/local/migration/Migrations.kt"
    if not mig_file.exists():
        print(f"❌ Не найден {mig_file}")
        return False

    text = mig_file.read_text(encoding="utf-8")

    if "MIGRATION_5_6" in text:
        print("⚠️  Migrations.kt уже содержит MIGRATION_5_6 — ничего не меняю")
        return True

    # Якорь — массив с MIGRATION_4_5
    old_block = """val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
)"""

    new_block = """val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
)"""

    if old_block not in text:
        print("❌ Не найден ожидаемый блок ALL_MIGRATIONS в Migrations.kt")
        print("   Внеси правку вручную: добавь MIGRATION_5_6 в массив.")
        return False

    text = text.replace(old_block, new_block, 1)

    backup = mig_file.with_suffix(".kt.bak")
    shutil.copy2(mig_file, backup)
    mig_file.write_text(text, encoding="utf-8")
    print(f"✅ Migrations.kt пропатчен (бэкап: {backup.name})")
    print("   добавлен MIGRATION_5_6 в ALL_MIGRATIONS")
    return True


# ============================================================
# MAIN
# ============================================================
def main() -> None:
    print("=" * 60)
    print("  Pathfinder Hub — Сессия A1")
    print("=" * 60)

    write_files()

    ok_db = patch_app_database()
    ok_mig = patch_migrations()

    print("\n" + "=" * 60)
    if ok_db and ok_mig:
        print("✅ Все патчи применены.")
        print("\nПроверь перед сборкой:")
        print("  1. AppDatabase.kt — version = 6, +3 entity, +3 DAO")
        print("  2. Migrations.kt  — MIGRATION_5_6 в массиве")
        print("\nСборка:")
        print("  ./gradlew assembleDebug")
    else:
        print("⚠️  Часть патчей не удалась. Смотри бэкапы *.kt.bak.")
        print(f"   AppDatabase.kt: {'OK' if ok_db else 'FAIL'}")
        print(f"   Migrations.kt:  {'OK' if ok_mig else 'FAIL'}")

    print("=" * 60)


if __name__ == "__main__":
    main()