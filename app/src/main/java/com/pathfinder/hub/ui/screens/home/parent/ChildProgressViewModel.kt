package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
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

data class ChildProgressUiState(
    val isLoading: Boolean = true,
    val child: UserEntity? = null,
    val levelCompletions: List<LevelCompletionEntity> = emptyList(),
    val honorCompletions: List<HonorCompletionEntity> = emptyList(),
    val achievements: List<AchievementProgressEntity> = emptyList(),
    val testAttempts: List<TestAttemptEntity> = emptyList(),
    val testTitles: Map<String, String> = emptyMap(),
    val pendingAttemptIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ChildProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val achievementRepository: AchievementRepository,
    private val testRepository: TestRepository,
) : ViewModel() {

    private val childId: String = savedStateHandle.get<String>("childId") ?: ""

    private val _state = MutableStateFlow(ChildProgressUiState())
    val state: StateFlow<ChildProgressUiState> = _state.asStateFlow()

    init {
        if (childId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не указан ребёнок") }
        } else {
            load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val child = userRepository.getUser(childId)
            _state.update { it.copy(child = child, isLoading = false) }
        }
        viewModelScope.launch {
            levelRepository.observeCompletions(childId).collect { list ->
                _state.update { it.copy(levelCompletions = list) }
            }
        }
        viewModelScope.launch {
            honorRepository.observeCompletions(childId).collect { list ->
                _state.update { it.copy(honorCompletions = list) }
            }
        }
        viewModelScope.launch {
            achievementRepository.observeEarned(childId).collect { list ->
                _state.update { it.copy(achievements = list) }
            }
        }
        viewModelScope.launch {
            testRepository.observeAttemptsByUser(childId).collect { list ->
                _state.update { it.copy(testAttempts = list) }
                resolveTestTitles(list)
                resolvePending(list)
            }
        }
        viewModelScope.launch {
            // lifecycle pull: подписка живёт пока живёт ViewModel
            testRepository.pullTestAttempts(childId)
        }
    }

    /**
     * Резолв названий тестов одним batch-запросом.
     * Fallback в UI: "Тест #xxxxxxxx" (первые 8 символов id).
     */
    private suspend fun resolveTestTitles(attempts: List<TestAttemptEntity>) {
        val ids = attempts.mapNotNull { it.testId }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            _state.update { it.copy(testTitles = emptyMap()) }
            return
        }
        val map = mutableMapOf<String, String>()
        ids.forEach { id ->
            val title = testRepository.getTest(id)?.title
            if (!title.isNullOrBlank()) map[id] = title
        }
        _state.update { it.copy(testTitles = map) }
    }

    /** Попытки, у которых есть непроверенные long_text. */
    private suspend fun resolvePending(attempts: List<TestAttemptEntity>) {
        val pending = attempts
            .filter { !it.passed }
            .filter { testRepository.countUnreviewedForAttempt(it.id) > 0 }
            .map { it.id }
            .toSet()
        _state.update { it.copy(pendingAttemptIds = pending) }
    }
}
