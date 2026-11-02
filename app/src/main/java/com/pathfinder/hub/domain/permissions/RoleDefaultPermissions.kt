package com.pathfinder.hub.domain.permissions

/**
 * Дефолтный набор прав для staff-роли при назначении/создании.
 *
 * Owner клуба (isOwnerClub=true) — все права через PermissionChecker, здесь не указан.
 * conference_admin — тоже все через PermissionChecker.
 * teen, parent, conference_secretary — не используют directorPermissions.
 *
 * co-director (director + isOwnerClub=false) — дефолт пуст, owner выдаёт вручную
 * через ManagePermissionsScreen.
 *
 * instructor/secretary — при назначении получают этот набор; owner может
 * изменить через ManagePermissionsScreen.
 */
object RoleDefaultPermissions {

    val DEFAULTS: Map<String, List<String>> = mapOf(

        // ============ INSTRUCTOR (наставник) ============
        "instructor" to listOf(
            "members.view",       // видит своих подопечных
            "learning.approve",   // проверка заданий, чек-листов
            "learning.homework",  // назначает домашки
            "moderation.review",  // модератор (по матрице)
            "qr.create",          // создаёт QR-станции (по решению)
        ),

        // ============ SECRETARY (секретарь) ============
        "secretary" to listOf(
            "members.view",
            "invites.create",
            "invites.revoke",
            "tasks.create",
            "reports.view",
            "reports.generate",
            "moderation.review",
            "learning.approve",
            "learning.homework",
        ),

        // ============ CO-DIRECTOR (director + isOwnerClub=false) ============
        // Пусто: owner выдаёт права вручную.
        "director" to emptyList(),
    )

    fun forRole(role: String): List<String> = DEFAULTS[role] ?: emptyList()
}
