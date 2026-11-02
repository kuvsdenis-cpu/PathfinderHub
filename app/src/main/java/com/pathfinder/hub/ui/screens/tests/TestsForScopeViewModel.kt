package com.pathfinder.hub.ui.screens.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestRow(
    val test: TestEntity,
    val passed: Boolean,
    val pendingReview: Boolean = false,
)

data class TestsForScopeUiState(
    val isLoading: Boolean = true,
    val rows: List<TestRow> = emptyList(),
)

@HiltViewModel
class TestsForScopeViewModel @Inject constructor(
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(TestsForScopeUiState())
    val state: StateFlow<TestsForScopeUiState> = _state.asStateFlow()

    fun load(scope: String, scopeId: String?) {
        viewModelScope.launch {
            val userId = sessionManager.getUserId()
            if (userId == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val tests = testRepository.getTestsForScope(clubId, scope, scopeId)
            if (tests.isEmpty()) {
                _state.update { it.copy(isLoading = false, rows = emptyList()) }
                return@launch
            }

            val passedIds = testRepository.getPassedTestIds(
                userId = userId,
                testIds = tests.map { it.id }
            ).toSet()

            val rows = tests.map { t ->
                val latest = testRepository.getLatestAttempt(userId, t.id)
                val pending = latest != null &&
                        !latest.passed &&
                        testRepository.countUnreviewedForAttempt(latest.id) > 0
                TestRow(
                    test = t,
                    passed = t.id in passedIds,
                    pendingReview = pending,
                )
            }
            _state.update { it.copy(isLoading = false, rows = rows) }
        }
    }
}
