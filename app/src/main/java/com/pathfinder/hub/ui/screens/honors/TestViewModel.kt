package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.repository.HonorRepository
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
    val errorMessage: String? = null
)

@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val submitTest: SubmitTestUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""
    private val _state = MutableStateFlow(TestUiState())
    val state: StateFlow<TestUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val qs = honorRepository.getRandomQuestions(honorId, 10)
            _state.update { it.copy(isLoading = false, questions = qs) }
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
        val correct = s.questions.count { q ->
            s.answers[q.id]?.equals(q.correctAnswer, ignoreCase = true) == true
        }
        viewModelScope.launch {
            val result = submitTest(userId, honorId, correct, s.questions.size)
            result.fold(
                onSuccess = { passed ->
                    val score = if (s.questions.isNotEmpty()) correct * 100 / s.questions.size else 0
                    _state.update { it.copy(isFinished = true, passed = passed, score = score) }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message) }
                }
            )
        }
    }
}
