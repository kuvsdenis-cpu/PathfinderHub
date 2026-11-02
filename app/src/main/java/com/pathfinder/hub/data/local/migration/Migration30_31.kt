package com.pathfinder.hub.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграция 30 -> 31. Сохранение choice-ответов в попытке теста.
 *
 *   test_attempts: добавляем choiceAnswers TEXT NOT NULL DEFAULT '{}'
 *
 * Формат: JSON-строка вида {"q_id_1":"A","q_id_2":"B"}.
 * Нужно для пересчёта passed после проверки long_text наставником.
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `test_attempts` ADD COLUMN `choiceAnswers` TEXT NOT NULL DEFAULT '{}'")
    }
}
