package com.pathfinder.hub.ui.learning.editor

/**
 * Человеческие названия типов заданий.
 * Технические ключи — в RequirementTaskEntity.type.
 */
object TaskTypeLabels {

    private val LABELS: Map<String, String> = mapOf(
        "short_text" to "Короткий ответ",
        "long_text" to "Развёрнутый ответ",
        "single_choice" to "Один из вариантов",
        "multi_choice" to "Несколько вариантов",
        "number" to "Число",
        "date" to "Дата",
        "checkbox" to "Галочка",
        "file" to "Файл",
        "cipher" to "Шифр",
        "crossword" to "Кроссворд",
        "fill_blank" to "Пропуски в тексте",
        "grouping" to "Группировка",
        "match" to "Сопоставление",
        "ordering" to "Порядок",
    )

    private val HINTS: Map<String, String> = mapOf(
        "short_text" to "Ввод одной строки. Проверяется вручную.",
        "long_text" to "Несколько строк / сочинение. Проверяется вручную.",
        "single_choice" to "Выбор одного ответа из списка. Автопроверка.",
        "multi_choice" to "Несколько правильных ответов. Автопроверка.",
        "number" to "Числовой ответ. Автопроверка.",
        "date" to "Дата (например, год). Проверяется вручную.",
        "checkbox" to "«Сделал / не сделал». Проверяется вручную.",
        "file" to "Загрузить документ / фото. Проверяется вручную.",
        "cipher" to "Расшифровать слово/фразу. Автопроверка.",
        "crossword" to "Заполнить кроссворд. Автопроверка.",
        "fill_blank" to "Вставить слова в ___. Автопроверка.",
        "grouping" to "Распределить элементы по группам. Автопроверка.",
        "match" to "Соединить левый и правый столбцы. Автопроверка.",
        "ordering" to "Расставить элементы в правильном порядке. Автопроверка.",
    )

    /** Порядок для чипов: сначала автопроверка, потом ручная. */
    val AUTO_TYPES: List<String> = listOf(
        "single_choice", "multi_choice", "number",
        "cipher", "crossword", "fill_blank", "grouping", "match", "ordering",
    )
    val MANUAL_TYPES: List<String> = listOf(
        "short_text", "long_text", "date", "checkbox", "file",
    )

    fun humanLabel(type: String): String = LABELS[type] ?: type
    fun humanHint(type: String): String = HINTS[type] ?: ""
}
