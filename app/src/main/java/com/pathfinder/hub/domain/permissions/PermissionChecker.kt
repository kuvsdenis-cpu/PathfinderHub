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

    fun hasPerm(user: UserEntity?, perm: String): Boolean {
        if (user == null) return false
        if (user.role != "director") return false
        if (user.isOwnerClub) return true
        return perm in user.directorPermissions
    }

    /** Публичный сахар: hasAny("a", "b", "c"). */
    fun hasAny(user: UserEntity?, vararg perms: String): Boolean =
        perms.any { hasPerm(user, it) }

    /** Секретарь конференции — отдельная роль, не club staff. */
    fun isConferenceSecretary(user: UserEntity?): Boolean =
        user?.role == "conference_secretary"

    /** Администратор конференции. */
    fun isConferenceAdmin(user: UserEntity?): Boolean =
        user?.role == "conference_admin"
}
