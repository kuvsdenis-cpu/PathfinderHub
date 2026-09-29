package com.pathfinder.hub.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Мягкая автопроверка контента для детской аудитории.
 * Возвращает:
 *  - "ok"       — можно публиковать сразу
 *  - "flagged"  — требуется ручная модерация
 *
 * Правила:
 *  1. Запрет нецензурной лексики (базовый список)
 *  2. Защита персональных данных (email, телефон, адрес)
 *  3. Защита от спама (повторы символов, капс)
 */
@Singleton
class AutoModerationService @Inject constructor() {

    // Базовый список запрещённых корней (без грубых слов — детская аудитория)
    private val bannedRoots = listOf(
        "бля", "хуй", "пизд", "еба", "сука", "дерьм", "мудак", "залуп", "жопа"
    )

    // Паттерны персональных данных
    private val emailRegex = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
    private val phoneRegex = Regex("""(\+?\d{1,3}[\s-]?)?\(?\d{3,4}\)?[\s-]?\d{2,4}[\s-]?\d{2,4}""")
    private val urlRegex = Regex("""(https?://|www\.)\S+""")

    fun check(text: String): String {
        if (text.isBlank()) return "ok"

        val lower = text.lowercase()

        // 1. Запрещённые корни
        if (bannedRoots.any { lower.contains(it) }) return "flagged"

        // 2. Персональные данные (дети не должны делиться контактами)
        if (emailRegex.containsMatchIn(text)) return "flagged"
        if (phoneRegex.containsMatchIn(text)) return "flagged"
        if (urlRegex.containsMatchIn(text)) return "flagged"

        // 3. Спам: слишком много повторов одного символа (например "ааааааааа")
        val grouped = groupRepeatedChars(lower)
        if (grouped.any { it.length > 6 }) return "flagged"

        // 4. Капс: больше 70% заглавных букв в длинных сообщениях
        if (text.length > 10) {
            val upperCount = text.count { it.isUpperCase() }
            val letterCount = text.count { it.isLetter() }
            if (letterCount > 0 && upperCount.toFloat() / letterCount > 0.7f) return "flagged"
        }

        return "ok"
    }

    private fun groupRepeatedChars(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var lastChar = text[0]
        current.append(lastChar)
        for (i in 1 until text.length) {
            val c = text[i]
            if (c == lastChar && c.isLetter()) {
                current.append(c)
            } else {
                if (current.length > 1) result.add(current.toString())
                current.clear()
                current.append(c)
                lastChar = c
            }
        }
        if (current.length > 1) result.add(current.toString())
        return result
    }
}