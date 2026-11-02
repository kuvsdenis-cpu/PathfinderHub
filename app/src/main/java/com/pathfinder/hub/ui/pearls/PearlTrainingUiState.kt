package com.pathfinder.hub.ui.pearls

/**
 * B8: интерактивная тренировка Жемчужин.
 * 5 режимов, выбор случайный на каждое задание.
 */
enum class TrainingMode {
    FILL_BLANKS,   // пропуски в стихе
    ASSEMBLE,      // собрать стих из перемешанных слов
    CHOOSE_WORD,   // выбрать пропущенное слово из вариантов
    FIRST_LETTERS, // ввести слово по первым буквам
    CHUNKS,        // собрать стих из фрагментов (по 2-3 слова)
}

/**
 * Задание, сгенерированное ViewModel для текущего шага тренировки.
 * Хранит исходные данные, чтобы UI мог отрисовать режим и проверить ответ.
 */
data class TrainingTask(
    val mode: TrainingMode,
    val words: List<String>,          // слова стиха (в правильном порядке)
    val blanks: List<Blank>,          // для FILL_BLANKS / CHOOSE_WORD / FIRST_LETTERS
    val shuffled: List<String>,       // для ASSEMBLE / CHUNKS (перемешанные токены)
    val chunkSize: Int = 3,           // для CHUNKS
)

/**
 * Один пропуск в стихе.
 * @param wordIndex индекс слова в [TrainingTask.words], которое скрыто.
 * @param options варианты для CHOOSE_WORD (null для FILL_BLANKS/FIRST_LETTERS).
 * @param correct правильный ответ (слово или первые буквы).
 */
data class Blank(
    val wordIndex: Int,
    val correct: String,
    val options: List<String>? = null,
)

/**
 * Состояние экрана тренировки.
 */
data class PearlTrainingUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val pearlId: String = "",
    val pearlText: String = "",          // полный текст стиха (для финала)
    val reference: String = "",          // «Ин. 3:16»
    val tasks: List<TrainingTask> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswers: List<String> = emptyList(),  // по одному ответу на задание
    val submitted: Boolean = false,      // текущее задание проверено
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val scorePercent: Int = 0,           // 0..100, лучший результат уже сохранён
    val isSaving: Boolean = false,
    val saveError: String? = null,
) {
    val currentTask: TrainingTask?
        get() = tasks.getOrNull(currentIndex)

    val total: Int
        get() = tasks.size

    val progressFraction: Float
        get() = if (tasks.isEmpty()) 0f
        else (currentIndex.toFloat() / tasks.size.toFloat()).coerceIn(0f, 1f)
}
