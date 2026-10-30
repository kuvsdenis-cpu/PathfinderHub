package com.pathfinder.hub.domain.permissions

import com.pathfinder.hub.data.local.entity.core.UserEntity

/**
 * Хелпер проверки прав директора.
 *
 * Логика:
 *   - role != "director" → false
 *   - isOwnerClub = true → все права
 *   - иначе → perm in directorPermissions
 */
object PermissionChecker {

    /**
     * Роли staff, чьи права хранятся в user.directorPermissions.
     * Имя поля историческое; семантически — «индивидуальные права staff».
     */
    private val STAFF_ROLES = setOf("director", "instructor", "secretary")

    fun hasPerm(user: UserEntity?, perm: String): Boolean {
        if (user == null) return false
        // Conference admin — все права (Firestore rules: isConferenceAdmin).
        if (user.role == "conference_admin") return true
        // Owner — все права.
        if (user.role == "director" && user.isOwnerClub) return true
        // Staff-роли читают индивидуальный набор.
        if (user.role in STAFF_ROLES) return perm in user.directorPermissions
        return false
    }

    /** Публичный сахар: hasAny("a", "b", "c"). */
    fun hasAny(user: UserEntity?, vararg perms: String): Boolean =
        perms.any { hasPerm(user, it) }

    /** Секретарь конференции — отдельная роль, не club staff. */
    fun isConferenceSecretary(user: UserEntity?): Boolean =
        user?.role == "conference_secretary"

    /**
     * Может ли пользователь отправлять в архив / восстанавливать.
     * Только director (owner) или co-director с правом archive.manage.
     */
    fun canArchive(user: UserEntity?): Boolean = hasPerm(user, "archive.manage")

    /** Администратор конференции. */
    fun isConferenceAdmin(user: UserEntity?): Boolean =
        user?.role == "conference_admin"
}
