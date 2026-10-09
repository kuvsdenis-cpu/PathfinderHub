#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K4 part 1 (model + migration) - 09.10.2026

- UserEntity: +isOwnerClub, +directorPermissions
- InviteEntity: +permissions
- Migration_8_9
- AppDatabase version 8 -> 9

Запуск:
    python fix_k4_part1.py
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
# 1. UserEntity.kt — +isOwnerClub, +directorPermissions
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/entity/core/UserEntity.kt", [
    ("""    val clubId: String?, val clubIds: List<String> = emptyList(),
    val conferenceId: String?, val birthDate: Date,""",
     """    val clubId: String?, val clubIds: List<String> = emptyList(),
    val isOwnerClub: Boolean = true,
    val directorPermissions: List<String> = emptyList(),
    val conferenceId: String?, val birthDate: Date,"""),
], skip_if_marker="isOwnerClub")


# ============================================================
# 2. InviteEntity.kt — +permissions
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/entity/auth/InviteEntity.kt", [
    ("""    val role: String, val createdBy: String, val createdAt: Date,""",
     """    val role: String,
    val permissions: List<String> = emptyList(),
    val createdBy: String, val createdAt: Date,"""),
], skip_if_marker="val permissions: List<String>")


# ============================================================
# 3. Migration_8_9.kt
# ============================================================
MIGR = '''package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 8 -> 9.
 *
 * Добавляет в users:
 *   - isOwnerClub (BOOLEAN, default 1) — существующие директора становятся owner
 *   - directorPermissions (TEXT, JSON-массив строк)
 *
 * Добавляет в invites:
 *   - permissions (TEXT, JSON-массив строк)
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN isOwnerClub INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE users ADD COLUMN directorPermissions TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE invites ADD COLUMN permissions TEXT NOT NULL DEFAULT '[]'")
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/migration/Migration_8_9.kt", MIGR)


# ============================================================
# 4. Migrations.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/migration/Migrations.kt", [
    ("""    MIGRATION_7_8,
)""",
     """    MIGRATION_7_8,
    MIGRATION_8_9,
)"""),
], skip_if_marker="MIGRATION_8_9")


# ============================================================
# 5. AppDatabase.kt — version 9
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/AppDatabase.kt", [
    ("""    version = 8,""",
     """    version = 9,"""),
], skip_if_marker="version = 9,")


# ============================================================
# 6. PermissionRegistry — список всех прав
# ============================================================
PERMS = '''package com.pathfinder.hub.domain.permissions

/**
 * Реестр прав директора.
 *
 * Owner клуба (isOwnerClub = true) имеет ВСЕ права.
 * Со-директор получает только явно перечисленные.
 */
object PermissionRegistry {

    // Группы для UI
    data class Group(val title: String, val permissions: List<Permission>)

    data class Permission(val key: String, val label: String)

    val GROUPS: List<Group> = listOf(
        Group("Участники", listOf(
            Permission("members.view", "Просмотр участников"),
            Permission("members.edit", "Изменение ролей"),
            Permission("members.delete", "Удаление участников"),
        )),
        Group("Инвайты", listOf(
            Permission("invites.create", "Создание инвайтов"),
            Permission("invites.revoke", "Отзыв инвайтов"),
        )),
        Group("Обучение", listOf(
            Permission("learning.approve", "Одобрение требований"),
            Permission("learning.homework", "Проверка домашки"),
            Permission("content.edit", "Редактирование контента"),
        )),
        Group("События и задания", listOf(
            Permission("events.create", "Создание событий"),
            Permission("events.edit", "Редактирование событий"),
            Permission("events.delete", "Удаление событий"),
            Permission("tasks.create", "Создание заданий"),
            Permission("challenges.create", "Создание челленджей"),
        )),
        Group("QR и активность", listOf(
            Permission("qr.create", "Создание QR-станций"),
            Permission("qr.delete", "Удаление QR-станций"),
        )),
        Group("Модерация", listOf(
            Permission("moderation.review", "Проверка сообщений"),
        )),
        Group("Отчёты", listOf(
            Permission("reports.view", "Просмотр отчётов"),
            Permission("reports.generate", "Генерация отчётов"),
        )),
        Group("Настройки клуба", listOf(
            Permission("settings.view", "Просмотр настроек"),
            Permission("settings.edit", "Изменение настроек"),
        )),
    )

    /** Все ключи прав. */
    val ALL: List<String> = GROUPS.flatMap { it.permissions.map { p -> p.key } }

    /** Права по умолчанию для со-директора (безопасный набор). */
    val DEFAULT_CO_DIRECTOR: List<String> = listOf(
        "members.view",
        "learning.approve",
        "learning.homework",
        "events.create",
        "events.edit",
        "tasks.create",
        "moderation.review",
        "reports.view",
    )

    fun labelOf(key: String): String =
        GROUPS.flatMap { it.permissions }.find { it.key == key }?.label ?: key
}
'''
write_new("app/src/main/java/com/pathfinder/hub/domain/permissions/PermissionRegistry.kt", PERMS)


print()
print("=" * 60)
print("K4 part 1 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - UserEntity: +isOwnerClub=true, +directorPermissions=[]")
print("  - InviteEntity: +permissions=[]")
print("  - Migration_8_9: ALTER users, ALTER invites")
print("  - AppDatabase: version 8 -> 9")
print("  - PermissionRegistry: список всех прав + группы + defaults")
print()
print("ВАЖНО:")
print("  Существующие директора автоматически становятся isOwnerClub=true")
print("  через DEFAULT 1 в миграции. Ничего не сломается.")
print()
print("Откат: .bak.<timestamp>")