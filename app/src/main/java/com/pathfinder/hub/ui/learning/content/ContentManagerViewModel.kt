package com.pathfinder.hub.ui.learning.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.TestRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
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

data class LevelWithCount(
    val level: LevelEntity,
    val requirementCount: Int,
    val sectionCount: Int
)

data class HonorWithCount(
    val honor: HonorEntity,
    val categoryName: String,
    val requirementCount: Int,
)

data class ContentManagerUiState(
    val isLoading: Boolean = true,
    val levels: List<LevelWithCount> = emptyList(),
    val honors: List<HonorWithCount> = emptyList(),
    val tests: List<TestEntity> = emptyList(),
    val clubId: String? = null,
    val canEdit: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ContentManagerViewModel @Inject constructor(
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val testRepository: TestRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentManagerUiState())
    val state: StateFlow<ContentManagerUiState> = _state.asStateFlow()

    init {
        loadStaticContent()
        observeClub()
    }

    /** levels + honors + categories — глобальные, не зависят от клуба. */
    private fun loadStaticContent() {
        viewModelScope.launch {
            try {
                val levels = levelRepository.observeLevels().first()
                val enrichedLevels = levels
                    .sortedBy { it.order }
                    .map { lvl ->
                        val reqs = levelRepository.getRequirements(lvl.id)
                        val sections = levelRepository.getSections(lvl.id)
                        LevelWithCount(
                            level = lvl,
                            requirementCount = reqs.size,
                            sectionCount = sections.size
                        )
                    }

                val honors = honorRepository.observeAllHonors().first()
                val categories = honorRepository.observeCategories().first()
                val categoriesById = categories.associateBy { it.id }
                val enrichedHonors = honors.map { h ->
                    val reqs = honorRepository.getRequirements(h.id)
                    HonorWithCount(
                        honor = h,
                        categoryName = categoriesById[h.categoryId]?.name ?: "",
                        requirementCount = reqs.size,
                    )
                }

                _state.update {
                    it.copy(levels = enrichedLevels, honors = enrichedHonors)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    /** clubId + canEdit + tests — зависят от user. */
    private fun observeClub() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user == null) return@collect
                val canEdit = PermissionChecker.hasPerm(user, "content.edit")
                        || user.role == "instructor"
                        || user.isOwnerClub
                _state.update { it.copy(canEdit = canEdit) }
            }
        }

        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(isLoading = false, clubId = null, tests = emptyList()) }
                        return@collectLatest
                    }
                    val tests = testRepository.observeAllForClub(clubId).first()
                    _state.update {
                        it.copy(isLoading = false, clubId = clubId, tests = tests)
                    }
                }
        }
    }
}
