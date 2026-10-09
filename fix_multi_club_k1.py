#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Multi-club K1 (model) - 09.10.2026

- UserEntity: +clubIds: List<String>
- Migration_7_8: ALTER TABLE users ADD COLUMN clubIds TEXT DEFAULT '[]'
- AppDatabase: version 7 -> 8
- firestore.rules: userClubIds() + isOwnClub() с fallback на clubId
- UserRepository: +setActiveClub(), +addClub(), +removeClub()

Запуск:
    python fix_multi_club_k1.py
"""

import re, shutil, sys
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
# 1. UserEntity.kt — +clubIds
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/entity/core/UserEntity.kt", [
    ("""    val clubId: String?, val conferenceId: String?, val birthDate: Date,""",
     """    val clubId: String?, val clubIds: List<String> = emptyList(),
    val conferenceId: String?, val birthDate: Date,"""),
], skip_if_marker="clubIds: List<String>")


# ============================================================
# 2. Migration_7_8.kt
# ============================================================
MIGR = '''package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 7 -> 8.
 *
 * Добавляет clubIds (JSON-массив строк) в users.
 * Заполняется clubId для существующих пользователей.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN clubIds TEXT NOT NULL DEFAULT '[]'")
        // Заполняем clubIds из clubId для уже существующих
        db.execSQL(
            """
            UPDATE users
            SET clubIds = '[\"' || clubId || '\"]'
            WHERE clubId IS NOT NULL AND clubId != ''
            """.trimIndent()
        )
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/migration/Migration_7_8.kt", MIGR)


# ============================================================
# 3. Migrations.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/migration/Migrations.kt", [
    ("""    MIGRATION_6_7,
)""",
     """    MIGRATION_6_7,
    MIGRATION_7_8,
)"""),
], skip_if_marker="MIGRATION_7_8")


# ============================================================
# 4. AppDatabase.kt — version 8
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/AppDatabase.kt", [
    ("""    version = 7,""",
     """    version = 8,"""),
], skip_if_marker="version = 8,")


# ============================================================
# 5. firestore.rules — userClubIds + isOwnClub
# ============================================================
patch("firestore.rules", [
    ("""    function userClubId() {
      return userDoc().clubId;
    }""",
     """    function userClubId() {
      return userDoc().clubId;
    }

    // Мульти-клуб: список всех клубов пользователя.
    // Fallback: если clubIds пуст (старый юзер) — [clubId].
    function userClubIds() {
      return userDoc().clubIds != null && userDoc().clubIds.size() > 0
        ? userDoc().clubIds
        : [userDoc().clubId];
    }"""),

    ("""    function isOwnClub(clubId) {
      return isSignedIn() && userClubId() == clubId;
    }""",
     """    function isOwnClub(clubId) {
      return isSignedIn() && clubId in userClubIds();
    }"""),
], skip_if_marker="function userClubIds()")


# ============================================================
# 6. UserRepository.kt — методы для мульти-клуба
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/UserRepository.kt", [
    ("""    suspend fun requestDeletion(id: String, s: String, at: Long) {
        dao.requestDeletion(id, s, at)
        dao.getUser(id)?.let {
            firestore.updateUser(it)
            syncQueueManager.enqueue("user", id, "UPDATE", it)
        }
    }""",
     """    suspend fun requestDeletion(id: String, s: String, at: Long) {
        dao.requestDeletion(id, s, at)
        dao.getUser(id)?.let {
            firestore.updateUser(it)
            syncQueueManager.enqueue("user", id, "UPDATE", it)
        }
    }

    // ---------- Мульти-клуб ----------

    /**
     * Сменить активный клуб.
     * clubId остаётся клубом по умолчанию для старых мест.
     */
    suspend fun setActiveClub(userId: String, clubId: String) {
        val current = dao.getUser(userId) ?: return
        if (current.clubId == clubId) return
        val updated = current.copy(clubId = clubId)
        dao.update(updated)
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", userId, "UPDATE", updated)
        Log.d(TAG, "setActiveClub: $userId → $clubId")
    }

    /**
     * Добавить клуб пользователю.
     * Первый добавленный становится активным.
     */
    suspend fun addClubToUser(userId: String, clubId: String) {
        val current = dao.getUser(userId) ?: return
        val newList = (current.clubIds + clubId).distinct()
        val updated = current.copy(
            clubIds = newList,
            clubId = current.clubId ?: clubId,
        )
        dao.update(updated)
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", userId, "UPDATE", updated)
        Log.d(TAG, "addClubToUser: $userId += $clubId (total=${newList.size})")
    }

    /**
     * Убрать клуб у пользователя.
     * Если удаляем активный — активным становится первый из оставшихся.
     */
    suspend fun removeClubFromUser(userId: String, clubId: String) {
        val current = dao.getUser(userId) ?: return
        val newList = current.clubIds.filter { it != clubId }
        val newActive = when {
            current.clubId != clubId -> current.clubId
            newList.isNotEmpty() -> newList.first()
            else -> null
        }
        val updated = current.copy(clubIds = newList, clubId = newActive)
        dao.update(updated)
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", userId, "UPDATE", updated)
        Log.d(TAG, "removeClubFromUser: $userId -= $clubId (total=${newList.size})")
    }"""),
], skip_if_marker="setActiveClub")


print()
print("=" * 60)
print("K1 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("  firebase deploy --only firestore:rules")
print()
print("Что сделано:")
print("  - UserEntity.clubIds: List<String> = emptyList()")
print("  - Migration_7_8: ALTER + backfill clubIds из clubId")
print("  - AppDatabase: version 7 -> 8")
print("  - firestore.rules: userClubIds() + isClubOwn() с fallback")
print("  - UserRepository: setActiveClub, addClubToUser, removeClubFromUser")
print()
print("Обратная совместимость:")
print("  - Старые юзеры: clubIds автоматически заполнен [clubId]")
print("  - Все вызовы user.clubId продолжают работать (это активный клуб)")
print("  - firestore.rules fallback: если clubIds пусто, используется clubId")
print()
print("Откат: .bak.<timestamp>")