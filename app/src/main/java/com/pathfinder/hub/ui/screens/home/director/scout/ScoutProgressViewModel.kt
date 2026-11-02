package com.pathfinder.hub.ui.screens.home.director.scout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScoutProgressUiState(
    val isLoading: Boolean = true,
    val scouts: List<ScoutSummary> = emptyList(),
    val allLevels: List<LevelEntity> = emptyList(),
    val filterLevelId: String? = null,
    val searchQuery: String = "",
    val filterMode: FilterMode = FilterMode.ALL,
    val errorMessage: String? = null,
)

enum class FilterMode { ALL, BEHIND, READY }

@HiltViewModel
class ScoutProgressViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScoutProgressUiState())
    val state: StateFlow<ScoutProgressUiState> = _state.asStateFlow()

    private var loadedClubId: String? = null

    fun setClubId(clubId: String?) {
        if (clubId.isNullOrBlank()) {
            _state.update { it.copy(isLoading = false, scouts = emptyList()) }
            return
        }
        if (clubId == loadedClubId) return
        loadedClubId = clubId
        load(clubId)
    }

    private fun load(clubId: String) {
        viewModelScope.launch {
            levelRepository.observeLevels().collect { levels ->
                _state.update { it.copy(allLevels = levels) }
            }
        }
        viewModelScope.launch {
            combine(
                userRepository.observeClubMembersByRoles(clubId, listOf("teen")),
                levelRepository.observeLevels(),
                levelRepository.observeProgressByClubTeens(clubId),
                levelRepository.observeCompletionsByClubTeens(clubId),
                honorRepository.observeProgressByClubTeens(clubId),
                honorRepository.observeCompletionsByClubTeens(clubId),
                levelRepository.observeAllRequirements(),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val teens = values[0] as List<UserEntity>
                val levels = values[1] as List<LevelEntity>
                val levelProgress = values[2] as List<LevelProgressEntity>
                val levelCompletions = values[3] as List<LevelCompletionEntity>
                val honorProgress = values[4] as List<HonorProgressEntity>
                val honorCompletions = values[5] as List<HonorCompletionEntity>
                val allLevelReqs = values[6] as List<LevelRequirementEntity>

                val completedByUser = levelCompletions.groupBy { it.userId }
                    .mapValues { (_, list) -> list.map { it.levelId }.toSet() }
                val levelProgressByUser = levelProgress.groupBy { it.userId }
                val honorProgressByUser = honorProgress.groupBy { it.userId }
                val honorCompletionsByUser = honorCompletions.groupBy { it.userId }
                val reqsByLevel = allLevelReqs.groupBy { it.levelId }

                val result = mutableListOf<ScoutSummary>()
                for (teen in teens) {
                    val completed = completedByUser[teen.id] ?: emptySet()
                    val currentLevel = levels.firstOrNull { it.id !in completed }
                    val currentLevelId = currentLevel?.id
                    val currentLevelName = currentLevel?.name

                    val reqsForLevel = if (currentLevelId != null) reqsByLevel[currentLevelId].orEmpty() else emptyList()
                    val approvedReqs = levelProgressByUser[teen.id].orEmpty()
                        .filter { it.levelId == currentLevelId && it.status == "approved" }
                        .map { it.requirementId }
                        .toSet()
                    val total = reqsForLevel.size
                    val approved = reqsForLevel.count { it.id in approvedReqs }
                    val percent = if (total > 0) (approved * 100) / total else 0

                    val userHonorProgress = honorProgressByUser[teen.id].orEmpty()
                    val userHonorCompletions = honorCompletionsByUser[teen.id].orEmpty()
                    val completedHonorIds = userHonorCompletions.map { it.honorId }.toSet()
                    val inProgressHonorIds = userHonorProgress.map { it.honorId }
                        .distinct()
                        .filter { it !in completedHonorIds }

                    result.add(
                        ScoutSummary(
                            user = teen,
                            currentLevelId = currentLevelId,
                            currentLevelName = currentLevelName,
                            levelRequirementsTotal = total,
                            levelRequirementsApproved = approved,
                            levelPercent = percent,
                            honorsInProgress = inProgressHonorIds.size,
                            honorsCompleted = completedHonorIds.size,
                        )
                    )
                }
                result.sortedBy { it.user.lastName.lowercase() }
            }.collect { list ->
                _state.update { it.copy(isLoading = false, scouts = list) }
            }
        }
    }

    fun onFilterLevel(levelId: String?) = _state.update { it.copy(filterLevelId = levelId) }
    fun onSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }
    fun onFilterMode(mode: FilterMode) = _state.update { it.copy(filterMode = mode) }
}
