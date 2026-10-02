package com.pathfinder.hub.ui.storage

enum class FileUploadContext(
    val storagePrefix: String,
    val allowedMimeTypes: String,
    val screenTitle: String
) {
    LEVEL_REQUIREMENT(
        storagePrefix = "level_requirements",
        allowedMimeTypes = "application/pdf,image/*",
        screenTitle = "Отчёт по требованию ступени"
    ),
    HONOR_REQUIREMENT(
        storagePrefix = "honor_requirements",
        allowedMimeTypes = "application/pdf,image/*,video/*",
        screenTitle = "Отчёт по специализации"
    ),
    CHAT(
        storagePrefix = "chats",
        allowedMimeTypes = "image/*,video/*,application/pdf",
        screenTitle = "Прикрепить файл"
    ),
    TASK(
        storagePrefix = "tasks",
        allowedMimeTypes = "application/pdf,image/*",
        screenTitle = "Отчёт по задаче"
    ),
    AVATAR(
        storagePrefix = "avatars",
        allowedMimeTypes = "image/*",
        screenTitle = "Загрузить аватар"
    ),
    EVENT(
        storagePrefix = "events",
        allowedMimeTypes = "application/pdf,image/*",
        screenTitle = "Материалы события"
    ),
    NEWSPAPER(
        storagePrefix = "newspaper",
        allowedMimeTypes = "application/pdf",
        screenTitle = "Выпуск газеты"
    ),
    GENERIC(
        storagePrefix = "uploads",
        allowedMimeTypes = "*/*",
        screenTitle = "Загрузить файл"
    );

    companion object {
        fun fromString(value: String?): FileUploadContext =
            entries.firstOrNull { it.name == value } ?: GENERIC
    }
}