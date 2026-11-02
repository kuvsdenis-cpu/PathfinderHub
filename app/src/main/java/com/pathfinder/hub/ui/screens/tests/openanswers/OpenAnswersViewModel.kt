package com.pathfinder.hub.ui.screens.tests.openanswers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.learning.TestAttemptEntity
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenAnswerRow(
    val attempt: TestAttemptEntity,
    val teenName: String,
    val testTitle: String,
    val unreviewedCount: Int,
)

data class OpenAnswersUiState(
    val isLoading: Boolean = true,
    val rows: List<OpenAnswerRow> = emptyList(),
)

@HiltViewModel
class OpenAnswersViewModel @Inject constructor(
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OpenAnswersUiState())
    val state: StateFlow<OpenAnswersUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            testRepository.observeAttemptsWithPendingOpenAnswers().collect { attempts ->
                val rows = attempts.map { a ->
                    val teen = userRepository.getUser(a.userId)
                    val teenName = teen?.let { "${it.firstName} ${it.lastName}".trim() }
                        ?.takeIf { it.isNotBlank() } ?: a.userId.take(8)
                    val testTitle = a.testId?.let { testRepository.getTest(it)?.title } ?: "—"
                    val cnt = testRepository.countUnreviewedForAttempt(a.id)
                    OpenAnswerRow(a, teenName, testTitle, cnt)
                }
                _state.update { it.copy(isLoading = false, rows = rows) }
            }
        }
    }
}
