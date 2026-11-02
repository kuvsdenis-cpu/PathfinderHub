package com.pathfinder.hub.ui.pearls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.bible.PearlEntity
import com.pathfinder.hub.data.repository.PearlRepository
import com.pathfinder.hub.data.sync.SyncQueueManager
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * B8: интерактивная тренировка Жемчужин.
 * 5 режимов, случайный выбор на каждое задание.
 */
@HiltViewModel
class PearlTrainingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pearlRepository: PearlRepository,
    private val userRepository: UserRepository,
    private val syncQueue: SyncQueueManager,
) : ViewModel() {

    private val pearlId: String = savedStateHandle.get<String>("pearlId") ?: ""

    private val _state = MutableStateFlow(PearlTrainingUiState(pearlId = pearlId))
    val state: StateFlow<PearlTrainingUiState> = _state.asStateFlow()

    private var cachedPearl: PearlEntity? = null
    private var cachedUserId: String = ""

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = syncQueue.getCurrentUserId()
                    ?: throw IllegalStateException("Нет пользователя")
                cachedUserId = userId

                val pearl = pearlRepository.observePearlById(pearlId).first()
                    ?: throw IllegalStateException("Жемчужина не найдена")
                cachedPearl = pearl

                if (pearl.isExam) {
                    throw IllegalStateException("Экзаменационные жемчужины не тренируются")
                }
                val text = pearl.text?.trim().orEmpty()
                if (text.isBlank()) {
                    throw IllegalStateException("У жемчужины нет текста для тренировки")
                }

                val reference = buildReference(pearl)
                val tasks = generateTasks(text, TASKS_PER_SESSION)

                _state.update {
                    it.copy(
                        isLoading = false,
                        pearlText = text,
                        reference = reference,
                        tasks = tasks,
                        userAnswers = List(tasks.size) { "" },
                        currentIndex = 0,
                        submitted = false,
                        correctCount = 0,
                        finished = false,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    /** Ответ пользователя на текущее задание. */
    fun onAnswerChanged(answer: String) {
        val s = _state.value
        if (s.submitted || s.finished) return
        val answers = s.userAnswers.toMutableList()
        answers[s.currentIndex] = answer
        _state.update { it.copy(userAnswers = answers) }
    }

    /** Проверить текущее задание. */
    fun onSubmit() {
        val s = _state.value
        if (s.submitted) return
        val task = s.currentTask ?: return
        val answer = s.userAnswers.getOrNull(s.currentIndex).orEmpty()
        val correct = checkAnswer(task, answer)
        _state.update {
            it.copy(
                submitted = true,
                correctCount = it.correctCount + if (correct) 1 else 0,
            )
        }
    }

    /** Перейти к следующему заданию или завершить. */
    fun onNext() {
        val s = _state.value
        if (!s.submitted) return
        val next = s.currentIndex + 1
        if (next >= s.tasks.size) {
            finish()
        } else {
            _state.update { it.copy(currentIndex = next, submitted = false) }
        }
    }

    /** Начать заново (новый случайный набор заданий). */
    fun onRetry() {
        val pearl = cachedPearl ?: return
        val text = pearl.text?.trim().orEmpty()
        if (text.isBlank()) return
        val tasks = generateTasks(text, TASKS_PER_SESSION)
        _state.update {
            it.copy(
                tasks = tasks,
                userAnswers = List(tasks.size) { "" },
                currentIndex = 0,
                submitted = false,
                correctCount = 0,
                finished = false,
                scorePercent = 0,
                saveError = null,
            )
        }
    }

    private fun finish() {
        val s = _state.value
        val total = s.tasks.size.coerceAtLeast(1)
        val percent = ((s.correctCount.toFloat() / total.toFloat()) * 100f).toInt()
            .coerceIn(0, 100)
        _state.update {
            it.copy(finished = true, scorePercent = percent, isSaving = true)
        }
        viewModelScope.launch {
            try {
                pearlRepository.saveTrainingResult(cachedUserId, pearlId, percent)
                _state.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Не удалось сохранить")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Генерация заданий
    // -----------------------------------------------------------------------

    private fun generateTasks(text: String, count: Int): List<TrainingTask> {
        val words = tokenize(text)
        if (words.size < 3) return emptyList()
        val modes = TrainingMode.entries
        return (0 until count).map {
            val mode = modes[Random.nextInt(modes.size)]
            buildTask(mode, words)
        }
    }

    private fun buildTask(mode: TrainingMode, words: List<String>): TrainingTask =
        when (mode) {
            TrainingMode.FILL_BLANKS,
            TrainingMode.FIRST_LETTERS -> {
                val blanks = pickBlanks(words, count = 2, withOptions = false)
                TrainingTask(mode, words, blanks, shuffled = emptyList())
            }
            TrainingMode.CHOOSE_WORD -> {
                val blanks = pickBlanks(words, count = 1, withOptions = true)
                TrainingTask(mode, words, blanks, shuffled = emptyList())
            }
            TrainingMode.ASSEMBLE -> {
                val shuffled = words.shuffled()
                TrainingTask(mode, words, blanks = emptyList(), shuffled = shuffled)
            }
            TrainingMode.CHUNKS -> {
                val chunks = chunkWords(words, chunkSize = 3).shuffled()
                TrainingTask(mode, words, blanks = emptyList(), shuffled = chunks)
            }
        }

    private fun pickBlanks(
        words: List<String>,
        count: Int,
        withOptions: Boolean,
    ): List<Blank> {
        val indices = words.indices
            .filter { words[it].length >= 3 }
            .shuffled()
            .take(count)
            .sorted()
        return indices.map { idx ->
            val correct = words[idx]
            val options = if (withOptions) {
                val distractors = words
                    .filterIndexed { i, _ -> i != idx }
                    .filter { it != correct && it.length >= 3 }
                    .shuffled()
                    .take(2)
                (distractors + correct).shuffled()
            } else null
            Blank(wordIndex = idx, correct = correct, options = options)
        }
    }

    private fun checkAnswer(task: TrainingTask, answer: String): Boolean =
        when (task.mode) {
            TrainingMode.FILL_BLANKS,
            TrainingMode.CHOOSE_WORD,
            TrainingMode.FIRST_LETTERS -> {
                val normalized = normalize(answer)
                val expected = task.blanks.joinToString(" ") { normalize(it.correct) }
                normalized == expected
            }
            TrainingMode.ASSEMBLE -> {
                normalize(answer) == normalize(task.words.joinToString(" "))
            }
            TrainingMode.CHUNKS -> {
                normalize(answer) == normalize(task.words.joinToString(" "))
            }
        }

    // -----------------------------------------------------------------------
    // Утилиты
    // -----------------------------------------------------------------------

    private fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun chunkWords(words: List<String>, chunkSize: Int): List<String> =
        words.chunked(chunkSize).map { it.joinToString(" ") }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[\\p{Punct}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        private fun buildReference(pearl: PearlEntity): String =
        pearl.reference.trim()

    companion object {
        /** Сколько заданий в одной сессии. */
        private const val TASKS_PER_SESSION = 5
    }
}
