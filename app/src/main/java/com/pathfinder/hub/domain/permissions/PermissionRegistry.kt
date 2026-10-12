package com.pathfinder.hub.domain.permissions

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
        Group("Финансы", listOf(
            Permission("finance.view", "Просмотр платежей"),
            Permission("finance.create", "Создание платежей"),
            Permission("finance.confirm", "Подтверждение оплат"),
        )),
        Group("Пользовательские группы", listOf(
            Permission("groups.manage", "Управление группами"),
        )),
        Group("Архив", listOf(
            Permission("archive.manage", "Отправка в архив и восстановление"),
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
