package com.pathfinder.hub.domain.permissions

import com.pathfinder.hub.data.local.entity.core.UserEntity

/**
 * Маппинг «плитка → права». Плитка видна, если у пользователя есть
 * ХОТЯ БЫ ОДНО из прав в списке. Пустой список — плитка видна всем staff
 * (и owner'у — у него PermissionChecker возвращает true на всё).
 *
 * Неизвестный ключ плитки → true (безопасный дефолт).
 * Parent/teen/conference роли сюда не ходят — у них права определяются ролью.
 */
object TilePermissions {

    const val INVITES      = "invites"
    const val MEMBERS      = "members"
    const val STAFF        = "staff"
    const val REPORTS      = "reports"
    const val EVENTS       = "events"
    const val TASKS        = "tasks"
    const val MODERATION   = "moderation"
    const val FINANCE      = "finance"
    const val CONTENT      = "content"
    const val CHECKLISTS   = "checklists"
    const val HOMEWORK     = "homework"
    const val QR           = "qr"
    const val GROUPS       = "groups"
    const val ARCHIVE      = "archive"
    const val SETTINGS     = "settings"
    const val SCOUT_PROGRESS = "scout_progress"
    const val BIBLE        = "bible"
    const val PEARLS       = "pearls"
    const val READING_PLAN = "reading_plan"
    const val INFO         = "info"
    const val CHATS        = "chats"

    private val map: Map<String, List<String>> = mapOf(
        INVITES      to listOf("invites.create", "invites.revoke"),
        MEMBERS      to listOf("members.view", "members.edit", "members.delete"),
        STAFF        to listOf("members.edit"),
        REPORTS      to listOf("reports.view", "reports.generate"),
        EVENTS       to listOf("events.create", "events.edit", "events.delete"),
        TASKS        to listOf("tasks.create"),
        MODERATION   to listOf("moderation.review"),
        FINANCE      to listOf("finance.view", "finance.create", "finance.confirm"),
        CONTENT      to listOf("content.edit"),
        CHECKLISTS   to listOf("learning.approve"),
        HOMEWORK     to listOf("learning.homework"),
        QR           to listOf("qr.create", "qr.delete"),
        GROUPS       to listOf("groups.manage"),
        ARCHIVE      to listOf("archive.manage"),
        SETTINGS     to listOf("settings.edit"),
        SCOUT_PROGRESS to listOf("learning.approve"),
        BIBLE        to emptyList(),
        PEARLS       to emptyList(),
        READING_PLAN to emptyList(),
        INFO         to emptyList(),
        CHATS        to emptyList(),
    )

    fun isVisible(user: UserEntity?, tile: String): Boolean {
        if (user == null) return false
        val perms = map[tile] ?: return false
        if (perms.isEmpty()) return true
        return PermissionChecker.hasAny(user, *perms.toTypedArray())
    }
}
