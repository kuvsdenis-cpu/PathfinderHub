package com.pathfinder.hub.ui.screens.tests.openanswers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.OpenAnswerReviewEntity
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ReviewRow(
    val question: TestQuestionEntity,
    val answer: String,
    val scoreText: String,       // "" если не проверено
    val reviewed: Boolean,
)

data class OpenAnswerReviewUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val teenName: String = "",
    val testTitle: String = "",
    val attempt: TestAttemptEntity? = null,
    val rows: List<ReviewRow> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class OpenAnswerReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val attemptId: String = savedStateHandle.get<String>("attemptId") ?: ""

    private val _state = MutableStateFlow(OpenAnswerReviewUiState())
    val state: StateFlow<OpenAnswerReviewUiState> = _state.asStateFlow()

    init {
        if (attemptId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Попытка не найдена") }
        } else {
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val attempt = testRepository.getAttemptById(attemptId)
                if (attempt == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Попытка не найдена") }
                    return@launch
                }
                val teen = userRepository.getUser(attempt.userId)
                val teenName = teen?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?.takeIf { it.isNotBlank() } ?: attempt.userId.take(8)
                val testTitle = attempt.testId?.let { testRepository.getTest(it)?.title } ?: "—"

                val questions = attempt.testId
                    ?.let { testRepository.getQuestionsForTest(it) }
                    ?: emptyList()
                val answers = testRepository.getOpenAnswersForAttempt(attemptId)
                val answersByQ = answers.associateBy { it.questionId }

                // Только long_text вопросы, для которых есть ответ.
                val rows = questions
                    .filter { it.type == "long_text" && answersByQ.containsKey(it.id) }
                    .map { q ->
                        val a = answersByQ[q.id]!!
                        ReviewRow(
                            question = q,
                            answer = a.answer,
                            scoreText = a.score?.toString() ?: "",
                            reviewed = a.score != null,
                        )
                    }

                _state.update {
                    it.copy(
                        isLoading = false,
                        attempt = attempt,
                        teenName = teenName,
                        testTitle = testTitle,
                        rows = rows,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }

    fun onScoreChange(questionId: String, value: String) {
        val filtered = value.filter { it.isDigit() }.take(3)
        _state.update { s ->
            s.copy(rows = s.rows.map { r ->
                if (r.question.id == questionId) r.copy(scoreText = filtered) else r
            })
        }
    }

    fun acceptAll(questionId: String) {
        _state.update { s ->
            s.copy(rows = s.rows.map { r ->
                if (r.question.id == questionId) r.copy(scoreText = r.question.points.toString())
                else r
            })
        }
    }

    fun rejectAll(questionId: String) {
        _state.update { s ->
            s.copy(rows = s.rows.map { r ->
                if (r.question.id == questionId) r.copy(scoreText = "0") else r
            })
        }
    }

    fun save() {
        val reviewerId = sessionManager.getUserId() ?: return
        val s = _state.value
        // Валидация: все баллы введены и в диапазоне
        for (r in s.rows) {
            val v = r.scoreText.toIntOrNull()
            if (v == null || v < 0 || v > r.question.points) {
                _state.update { it.copy(errorMessage = "Проверьте баллы (0..${r.question.points})") }
                return
            }
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val now = Date()
                s.rows.forEach { r ->
                    testRepository.upsertOpenAnswer(
                        OpenAnswerReviewEntity(
                            attemptId = attemptId,
                            questionId = r.question.id,
                            answer = r.answer,
                            score = r.scoreText.toIntOrNull(),
                            reviewedBy = reviewerId,
                            reviewedAt = now,
                        )
                    )
                }
                if (testRepository.countUnreviewedForAttempt(attemptId) == 0) {
                    testRepository.recalcAttemptAfterReview(attemptId)
                }
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
