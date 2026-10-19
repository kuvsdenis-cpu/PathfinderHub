package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.domain.usecase.learning.SubmitTestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestUiState(
    val isLoading: Boolean = true,
    val questions: List<TestQuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val isFinished: Boolean = false,
    val passed: Boolean = false,
    val score: Int = 0,
    val passScore: Int = 90,
    val attemptId: String = "",
    val pendingReview: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val testRepository: TestRepository,
    private val submitTest: SubmitTestUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val testId: String = savedStateHandle.get<String>("testId") ?: ""
    private val _state = MutableStateFlow(TestUiState())
    val state: StateFlow<TestUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (testId.isBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Тест не найден") }
                return@launch
            }
            val test = testRepository.getTest(testId)
            if (test == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Тест не найден") }
                return@launch
            }
            val qs = testRepository.getQuestionsForTest(testId)
            _state.update {
                it.copy(
                    isLoading = false,
                    questions = qs,
                    passScore = test.passScore,
                )
            }

            // Pull: если тест клубный — подписаться на свежие вопросы из Firestore.
            // viewModelScope отменит подписку при onCleared().
            val clubId = test.clubId
            if (!clubId.isNullOrBlank()) {
                launch {
                    try {
                        testRepository.pullTestQuestions(clubId, testId)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    fun selectAnswer(questionId: String, answer: String) {
        _state.update { it.copy(answers = it.answers + (questionId to answer)) }
    }

    fun next() {
        val s = _state.value
        if (s.currentIndex < s.questions.lastIndex) {
            _state.update { it.copy(currentIndex = s.currentIndex + 1) }
        } else {
            finish()
        }
    }

    fun previous() {
        val s = _state.value
        if (s.currentIndex > 0) {
            _state.update { it.copy(currentIndex = s.currentIndex - 1) }
        }
    }

    private fun finish() {
        val s = _state.value
        val userId = sessionManager.getUserId() ?: return
        val attemptId = java.util.UUID.randomUUID().toString()

        val totalPoints = s.questions.sumOf { it.points }.coerceAtLeast(1)
        val earnedPoints = s.questions.sumOf { q ->
            if (q.type == "long_text") 0
            else if (s.answers[q.id]?.equals(q.correctAnswer, ignoreCase = true) == true) q.points
            else 0
        }
        val choiceAnswers = buildChoiceAnswersJson(s)

        val openAnswers = s.questions
            .filter { it.type == "long_text" }
            .mapNotNull { q ->
                s.answers[q.id]?.takeIf { it.isNotBlank() }?.let { q.id to it }
            }

        viewModelScope.launch {
            val result = submitTest(
                u = userId,
                attemptId = attemptId,
                testId = testId,
                earnedPoints = earnedPoints,
                totalPoints = totalPoints,
                passScore = s.passScore,
                choiceAnswers = choiceAnswers,
                openAnswers = openAnswers,
            )
            result.fold(
                onSuccess = { passed ->
                    val score = earnedPoints * 100 / totalPoints
                    val pending = openAnswers.isNotEmpty() &&
                            testRepository.countUnreviewedForAttempt(attemptId) > 0
                    _state.update {
                        it.copy(
                            isFinished = true,
                            passed = passed,
                            score = score,
                            attemptId = attemptId,
                            pendingReview = pending,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message) }
                }
            )
        }
    }

    private fun buildChoiceAnswersJson(s: TestUiState): String {
        return try {
            val obj = org.json.JSONObject()
            s.questions.forEach { q ->
                if (q.type != "long_text") {
                    val a = s.answers[q.id]
                    if (!a.isNullOrBlank()) obj.put(q.id, a)
                }
            }
            obj.toString()
        } catch (_: Exception) { "{}" }
    }
}
