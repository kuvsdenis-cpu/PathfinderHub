package com.pathfinder.hub.ui.screens.home.director.sections

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestWithStats(
    val test: TestEntity,
    val totalTeens: Int,
    val passedCount: Int,
    val failedCount: Int,
    val notAttemptedCount: Int,
)

data class StaffTestsUiState(
    val isLoading: Boolean = true,
    val tests: List<TestWithStats> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class StaffTestsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffTestsUiState())
    val state: StateFlow<StaffTestsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeTests(uid)
        }
    }

    private fun observeTests(userId: String) {
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(isLoading = false, tests = emptyList()) }
                        return@collectLatest
                    }
                    testRepository.observeAllForClub(clubId).collect { list ->
                        val teens = try {
                            userRepository.observeClubMembersByRole(clubId, "teen").first()
                        } catch (_: Exception) { emptyList() }

                        val withStats = list.map { test ->
                            var passed = 0
                            teens.forEach { teen ->
                                val attempt = testRepository.getLatestAttempt(teen.id, test.id)
                                if (attempt?.passed == true) passed++
                            }
                            TestWithStats(
                                test = test,
                                totalTeens = teens.size,
                                passedCount = passed,
                                failedCount = 0,
                                notAttemptedCount = teens.size - passed,
                            )
                        }
                        _state.update { it.copy(isLoading = false, tests = withStats) }
                    }
                }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null) }
}
