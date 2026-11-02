package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 29 -> 30. Разделение scope "task" и "homework".
 *
 *   До:    scope = "homework" был для задач (TaskEntity).
 *   После: scope = "task"     — для задач,
 *          scope = "homework" — для домашек (HomeworkEntity).
 *
 * Переезд только тех тестов, чей scopeId действительно указывает
 * на задачу. Если scopeId = HomeworkEntity.id — не трогаем.
 *
 * Если таких тестов нет — UPDATE затронет 0 строк (no-op).
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            UPDATE `tests` SET `scope` = 'task'
            WHERE `scope` = 'homework'
              AND `scopeId` IN (SELECT `id` FROM `tasks`)
        """.trimIndent())
    }
}
