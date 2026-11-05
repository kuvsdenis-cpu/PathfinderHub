package com.pathfinder.hub.ui.screens.home.director.sections

/**
 * Словари локализации для аудита и других UI-компонентов.
 * Ключи  английские (как в AuditLogEntity.targetType/action и в JSON newValue).
 * Значения  русские.
 */
object EnumLabels {
    val TYPE_LABELS: Map<String, String> = mapOf(
        "user" to "Пользователь",
        "event" to "Событие",
        "event_participant" to "Участник события",
        "comment" to "Комментарий",
        "task" to "Задача",
        "assignment" to "Назначение",
        "progress" to "Прогресс",
        "report" to "Отчёт",
        "settings" to "Настройки",
        "honor" to "Специализация",
        "level" to "Ступень",
        "test" to "Тест",
        "book" to "Книга",
        "custom_group" to "Группа",
        "club" to "Клуб",
        "invite" to "Инвайт",
    )

    val ACTION_LABELS: Map<String, String> = mapOf(
        "create" to "Создано",
        "update" to "Обновлено",
        "delete" to "Удалено",
        "invite" to "Приглашение",
        "approve" to "Одобрено",
        "reject" to "Отклонено",
        "hide" to "Скрыто",
        "restore" to "Восстановлено",
        "role_change" to "Смена роли",
        "archive" to "В архив",
        "publish" to "Опубликовано",
        "accept" to "Принято",
        "decline" to "Отклонено",
        "attend" to "Посещено",
    )

    val FIELD_LABELS: Map<String, String> = mapOf(
        "id" to "ID",
        "title" to "Название",
        "name" to "Имя",
        "description" to "Описание",
        "status" to "Статус",
        "type" to "Тип",
        "level" to "Уровень",
        "role" to "Роль",
        "email" to "Email",
        "firstName" to "Имя",
        "lastName" to "Фамилия",
        "clubId" to "Клуб",
        "clubIds" to "Клубы",
        "targetGroup" to "Группа",
        "dateStart" to "Начало",
        "dateEnd" to "Окончание",
        "location" to "Место",
        "createdBy" to "Создал",
        "createdAt" to "Создано",
        "updatedAt" to "Обновлено",
        "action" to "Действие",
        "reason" to "Причина",
    )

    val VALUE_LABELS: Map<String, String> = mapOf(
        "invited" to "Приглашён",
        "accepted" to "Принято",
        "declined" to "Отклонено",
        "attended" to "Посещено",
        "missed" to "Пропущено",
        "published" to "Опубликовано",
        "draft" to "Черновик",
        "archived" to "В архиве",
        "active" to "Активен",
        "pending" to "Ожидает",
        "suspended" to "Приостановлен",
        "deleted" to "Удалён",
        "approved" to "Одобрено",
        "rejected" to "Отклонено",
        "submitted" to "Отправлено",
        "teen" to "Следопыт",
        "parent" to "Родитель",
        "instructor" to "Наставник",
        "director" to "Директор",
        "secretary" to "Секретарь",
        // Причины модерации (fallback для аудита старых записей)
        "spam" to "Спам",
        "off_topic" to "Не по теме",
        "inappropriate" to "Неприемлемое содержание",
        "profanity" to "Нецензурная лексика",
        "advertising" to "Реклама",
        "duplicate" to "Дубликат",
        "low_quality" to "Низкое качество",
        "review_timeout" to "Истёк срок проверки",
        "custom" to "Другое",
    )

    val MEETING_DAY: Map<String, String> = mapOf(
        "saturday" to "Суббота",
        "sunday" to "Воскресенье",
        "monday" to "Понедельник",
        "tuesday" to "Вторник",
        "wednesday" to "Среда",
        "thursday" to "Четверг",
        "friday" to "Пятница",
        "sabbath" to "Суббота",
    )

    val FREQUENCY: Map<String, String> = mapOf(
        "weekly" to "Еженедельно",
        "biweekly" to "Раз в две недели",
        "monthly" to "Ежемесячно",
        "once" to "Однократно",
    )

    fun labelForMeetingDay(key: String): String = MEETING_DAY[key] ?: key
    fun labelForFrequency(key: String): String = FREQUENCY[key] ?: key

    fun labelForType(key: String): String = TYPE_LABELS[key] ?: key
    fun labelForAction(key: String): String = ACTION_LABELS[key] ?: key
    fun labelForField(key: String): String = FIELD_LABELS[key] ?: key
    fun labelForValue(value: String): String = VALUE_LABELS[value] ?: value
}