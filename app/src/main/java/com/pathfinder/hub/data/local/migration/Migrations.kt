package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration

/**
 * Список всех миграций БД.
 *
 * Room сам применяет миграции по цепочке версий:
 *   1 → 2 → 3 → ...
 *
 * Порядок в массиве НЕ важен — Room сортирует по версиям.
 *
 * ⚠️ При добавлении новой миграции (например, MIGRATION_2_3):
 *   1. Создайте файл Migration_2_3.kt с объектом MIGRATION_2_3.
 *   2. Поднимите version в @Database (AppDatabase.kt) до 3.
 *   3. Добавьте MIGRATION_2_3 в этот массив.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
)