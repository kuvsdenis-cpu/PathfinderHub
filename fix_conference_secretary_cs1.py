#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Conference secretary CS1 (model) - 09.10.2026

- InviteEntity: +conferenceId
- Migration_10_11: ALTER TABLE invites
- AppDatabase: version 10 -> 11
- FirestoreInviteDataSource: mapToEntity/mapFromEntity +conferenceId
- CreateInviteUseCase: поддержка conference_secretary
- RegisterByInviteUseCase: при conference_secretary — conferenceId
- UserDao: observeConferenceMembersByRole
- UserRepository: обёртка
- PermissionChecker: isConferenceSecretary
- firestore.rules: isConferenceSecretary() + правила

Запуск:
    python fix_conference_secretary_cs1.py
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
# 1. InviteEntity.kt — +conferenceId
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/entity/auth/InviteEntity.kt", [
    ("""@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String, val code: String, val clubId: String,
    val role: String,""",
     """@Entity(tableName = "invites")
data class InviteEntity(
    @PrimaryKey val id: String, val code: String, val clubId: String,
    val conferenceId: String? = null,
    val role: String,"""),
], skip_if_marker="val conferenceId: String?")


# ============================================================
# 2. Migration_10_11.kt
# ============================================================
MIGR = '''package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 10 -> 11.
 *
 * Добавляет в invites колонку conferenceId — для инвайтов
 * на роль conference_secretary (привязка к конференции,
 * а не к клубу).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE invites ADD COLUMN conferenceId TEXT")
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/local/migration/Migration_10_11.kt", MIGR)


# ============================================================
# 3. Migrations.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/migration/Migrations.kt", [
    ("""    MIGRATION_9_10,
)""",
     """    MIGRATION_9_10,
    MIGRATION_10_11,
)"""),
], skip_if_marker="MIGRATION_10_11")


# ============================================================
# 4. AppDatabase.kt — version 11
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/AppDatabase.kt", [
    ("""    version = 10,""",
     """    version = 11,"""),
], skip_if_marker="version = 11,")


# ============================================================
# 5. FirestoreInviteDataSource.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/remote/FirestoreInviteDataSource.kt", [
    # mapToEntity: +conferenceId
    ("""        return InviteEntity(
            id = data["id"] as? String ?: "",
            code = data["code"] as? String ?: "",
            clubId = data["clubId"] as? String ?: "",
            role = data["role"] as? String ?: "teen",""",
     """        return InviteEntity(
            id = data["id"] as? String ?: "",
            code = data["code"] as? String ?: "",
            clubId = data["clubId"] as? String ?: "",
            conferenceId = data["conferenceId"] as? String,
            role = data["role"] as? String ?: "teen","""),

    # mapFromEntity: +conferenceId
    ("""        return mapOf(
            "id" to invite.id,
            "code" to invite.code,
            "clubId" to invite.clubId,
            "role" to invite.role,""",
     """        return mapOf(
            "id" to invite.id,
            "code" to invite.code,
            "clubId" to invite.clubId,
            "conferenceId" to invite.conferenceId,
            "role" to invite.role,"""),
], skip_if_marker='"conferenceId" to invite.conferenceId')


# ============================================================
# 6. InviteRepository.createInvite — +conferenceId
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/InviteRepository.kt", [
    ("""    suspend fun createInvite(
        clubId: String,
        role: String,
        createdBy: String,
        lifetimeDays: Int = 7,
        permissions: List<String> = emptyList(),
    ): InviteEntity {
        val now = Date()
        val expiresAt = Date(now.time + lifetimeDays * 24L * 3600 * 1000)
        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            role = role,
            permissions = permissions,
            createdBy = createdBy,""",
     """    suspend fun createInvite(
        clubId: String,
        role: String,
        createdBy: String,
        lifetimeDays: Int = 7,
        permissions: List<String> = emptyList(),
        conferenceId: String? = null,
    ): InviteEntity {
        val now = Date()
        val expiresAt = Date(now.time + lifetimeDays * 24L * 3600 * 1000)
        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            conferenceId = conferenceId,
            role = role,
            permissions = permissions,
            createdBy = createdBy,"""),
], skip_if_marker="conferenceId: String? = null,")


# ============================================================
# 7. CreateInviteUseCase — поддержка conference_secretary
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/usecase/director/CreateInviteUseCase.kt", [
    ("""    suspend operator fun invoke(
        role: String,
        clubId: String? = null,
        permissions: List<String> = emptyList(),
    ): Result<InviteEntity> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("Нет активной сессии"))

            val user = userRepository.getUser(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            if (user.role != "director") {
                return Result.failure(Exception("Только директор может создавать инвайты"))
            }

            val targetClubId = clubId ?: user.clubId
                ?: return Result.failure(Exception("Не указан клуб"))

            // Проверяем, что этот клуб действительно у директора
            val available = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
            if (targetClubId !in available) {
                return Result.failure(Exception("Нет доступа к этому клубу"))
            }

            val invite = inviteRepository.createInvite(
                clubId = targetClubId,
                role = role,
                createdBy = userId,
                permissions = if (role == "director") permissions else emptyList(),
            )
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }""",
     """    suspend operator fun invoke(
        role: String,
        clubId: String? = null,
        permissions: List<String> = emptyList(),
    ): Result<InviteEntity> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("Нет активной сессии"))

            val user = userRepository.getUser(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            // ============ Conference secretary ============
            // Создаётся только conference_admin'ом, привязка к conferenceId.
            if (role == "conference_secretary") {
                if (user.role != "conference_admin") {
                    return Result.failure(Exception(
                        "Только администратор конференции может приглашать секретаря конференции"
                    ))
                }
                val confId = user.conferenceId
                    ?: return Result.failure(Exception("У пользователя нет конференции"))
                val invite = inviteRepository.createInvite(
                    clubId = "",
                    conferenceId = confId,
                    role = "conference_secretary",
                    createdBy = userId,
                )
                return Result.success(invite)
            }

            // ============ Остальные роли — только director ============
            if (user.role != "director") {
                return Result.failure(Exception("Только директор может создавать инвайты"))
            }

            val targetClubId = clubId ?: user.clubId
                ?: return Result.failure(Exception("Не указан клуб"))

            val available = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
            if (targetClubId !in available) {
                return Result.failure(Exception("Нет доступа к этому клубу"))
            }

            val invite = inviteRepository.createInvite(
                clubId = targetClubId,
                role = role,
                createdBy = userId,
                permissions = if (role == "director") permissions else emptyList(),
            )
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }"""),
], skip_if_marker="conference_secretary")


# ============================================================
# 8. RegisterByInviteUseCase — обработка conference_secretary
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/usecase/auth/RegisterByInviteUseCase.kt", [
    ("""        val user = UserEntity(
            id = uid,
            email = email,
            emailVerified = false,
            firstName = firstName,
            lastName = lastName,
            role = invite.role,
            clubId = invite.clubId,
            clubIds = listOf(invite.clubId),
            isOwnerClub = invite.role != "director",
            directorPermissions = if (invite.role == "director") invite.permissions else emptyList(),
            conferenceId = null,""",
     """        val isConfSec = invite.role == "conference_secretary"
        val user = UserEntity(
            id = uid,
            email = email,
            emailVerified = false,
            firstName = firstName,
            lastName = lastName,
            role = invite.role,
            clubId = if (isConfSec) null else invite.clubId,
            clubIds = if (isConfSec) emptyList() else listOf(invite.clubId),
            isOwnerClub = invite.role !in listOf("director", "conference_secretary"),
            directorPermissions = if (invite.role == "director") invite.permissions else emptyList(),
            conferenceId = if (isConfSec) invite.conferenceId else null,"""),
], skip_if_marker="val isConfSec = invite.role")


# ============================================================
# 9. UserDao — observeConferenceMembersByRole
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/local/dao/UserDao.kt", [
    ("""    @Query("SELECT * FROM users WHERE conferenceId = :conferenceId")
    fun observeConferenceUsers(conferenceId: String): Flow<List<UserEntity>>""",
     """    @Query("SELECT * FROM users WHERE conferenceId = :conferenceId")
    fun observeConferenceUsers(conferenceId: String): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE conferenceId = :conferenceId AND role = :role")
    fun observeConferenceMembersByRole(conferenceId: String, role: String): Flow<List<UserEntity>>"""),
], skip_if_marker="observeConferenceMembersByRole")


# ============================================================
# 10. UserRepository — обёртка
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/UserRepository.kt", [
    ("""    fun observeClubMembersByRole(clubId: String, role: String): Flow<List<UserEntity>> =
        dao.observeClubMembersByRole(clubId, role)""",
     """    fun observeClubMembersByRole(clubId: String, role: String): Flow<List<UserEntity>> =
        dao.observeClubMembersByRole(clubId, role)

    fun observeConferenceMembers(conferenceId: String): Flow<List<UserEntity>> =
        dao.observeConferenceUsers(conferenceId)

    fun observeConferenceMembersByRole(conferenceId: String, role: String): Flow<List<UserEntity>> =
        dao.observeConferenceMembersByRole(conferenceId, role)"""),
], skip_if_marker="observeConferenceMembersByRole")


# ============================================================
# 11. PermissionChecker — isConferenceSecretary
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/permissions/PermissionChecker.kt", [
    ("""    /** Публичный сахар: hasAny("a", "b", "c"). */
    fun hasAny(user: UserEntity?, vararg perms: String): Boolean =
        perms.any { hasPerm(user, it) }
}""",
     """    /** Публичный сахар: hasAny("a", "b", "c"). */
    fun hasAny(user: UserEntity?, vararg perms: String): Boolean =
        perms.any { hasPerm(user, it) }

    /** Секретарь конференции — отдельная роль, не club staff. */
    fun isConferenceSecretary(user: UserEntity?): Boolean =
        user?.role == "conference_secretary"

    /** Администратор конференции. */
    fun isConferenceAdmin(user: UserEntity?): Boolean =
        user?.role == "conference_admin"
}"""),
], skip_if_marker="isConferenceSecretary")


# ============================================================
# 12. firestore.rules
# ============================================================
patch("firestore.rules", [
    ("""    function isClubStaff() {
      return isSignedIn() && (userRole() in ['director', 'secretary', 'instructor']);
    }""",
     """    function isClubStaff() {
      return isSignedIn() && (userRole() in ['director', 'secretary', 'instructor']);
    }

    function isConferenceSecretary() {
      return isSignedIn() && userRole() == 'conference_secretary';
    }

    function userConferenceId() {
      return userDoc().conferenceId;
    }"""),

    ("""    match /conferences/{confId} {
      allow read: if isSignedIn();
      allow write: if isConferenceAdmin();
    }""",
     """    match /conferences/{confId} {
      allow read: if isSignedIn();
      allow write: if isConferenceAdmin()
        || (isConferenceSecretary() && userConferenceId() == confId);
    }

    // ---- Conference members (секретарь конференции) ----
    match /conferenceMembers/{memberId} {
      allow read: if isSignedIn() && (
        isConferenceAdmin()
        || (isConferenceSecretary() && userConferenceId() == resource.data.conferenceId)
      );
      allow write: if isConferenceAdmin()
        || (isConferenceSecretary() && userConferenceId() == request.resource.data.conferenceId);
    }

    // ---- Conference events (мероприятия конференции) ----
    match /conferenceEvents/{eventId} {
      allow read: if isSignedIn();
      allow create, update: if isConferenceAdmin()
        || (isConferenceSecretary() && userConferenceId() == request.resource.data.conferenceId);
      allow delete: if isConferenceAdmin();
    }"""),
], skip_if_marker="isConferenceSecretary()")

patch("firestore.rules", [
    # invites: разрешаем conference_admin создавать
    ("""    match /invites/{code} {
      allow read: if true;
      allow create: if isSignedIn()
        && hasPerm("invites.create")
        && request.resource.data.createdBy == uid()
        && request.resource.data.status == 'active';""",
     """    match /invites/{code} {
      allow read: if true;
      allow create: if isSignedIn()
        && (hasPerm("invites.create") || isConferenceAdmin())
        && request.resource.data.createdBy == uid()
        && request.resource.data.status == 'active';"""),
], skip_if_marker="(hasPerm(\"invites.create\") || isConferenceAdmin())")


print()
print("=" * 60)
print("CS1 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("  firebase deploy --only firestore:rules")
print()
print("Что сделано:")
print("  - InviteEntity: +conferenceId: String?")
print("  - Migration_10_11: ALTER TABLE invites ADD conferenceId")
print("  - AppDatabase: version 10 -> 11")
print("  - FirestoreInviteDataSource: mapToEntity / mapFromEntity +conferenceId")
print("  - InviteRepository.createInvite: +conferenceId")
print("  - CreateInviteUseCase: ветка 'conference_secretary' (только conference_admin)")
print("  - RegisterByInviteUseCase: при conference_secretary clubId=null, conferenceId=invite")
print("  - UserDao: observeConferenceMembersByRole")
print("  - UserRepository: observeConferenceMembers + ByRole")
print("  - PermissionChecker: isConferenceSecretary + isConferenceAdmin")
print("  - firestore.rules: isConferenceSecretary(), conferenceMembers, conferenceEvents,")
print("      invites.create разрешён conference_admin")
print()
print("Проверка:")
print("  - Сборка прошла, миграция 10->11 не упала")
print("  - firebase deploy без ошибок")
print()
print("Откат: .bak.<timestamp>")