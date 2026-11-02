package com.pathfinder.hub.ui.screens.home.director.scout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ScoutDetailUiState(
    val isLoading: Boolean = true,
    val detail: ScoutDetail? = null,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
    val showAwardDialog: Boolean = false,
    val allHonors: List<HonorEntity> = emptyList(),
)

private data class ScoutBase(
    val user: com.pathfinder.hub.data.local.entity.core.UserEntity?,
    val levels: List<LevelEntity>,
    val levelCompletions: List<LevelCompletionEntity>,
    val levelProgress: List<LevelProgressEntity>,
)

private data class ScoutHonors(
    val honorCompletions: List<HonorCompletionEntity>,
    val honorProgress: List<HonorProgressEntity>,
    val allLevelReqs: List<LevelRequirementEntity>,
    val allHonorReqs: List<HonorRequirementEntity>,
    val allHonors: List<HonorEntity>,
)

@HiltViewModel
class ScoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val achievementRepository: AchievementRepository,
    private val sessionManager: SessionManager,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val scoutId: String = savedStateHandle.get<String>("scoutId") ?: ""

    private val _state = MutableStateFlow(ScoutDetailUiState())
    val state: StateFlow<ScoutDetailUiState> = _state.asStateFlow()

    init {
        if (scoutId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не указан следопыт") }
        } else {
            load()
            loadAllHonors()
        }
    }

    /** Перегенерировать presigned URL отчёта (TTL 7 дней). */
    suspend fun refreshAttachmentUrl(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            storageRepository.refreshPresignedUrl(url, ifFreshHours = 24)
        }

    private fun loadAllHonors() {
        viewModelScope.launch {
            honorRepository.observeAllHonors().collect { list ->
                _state.update { it.copy(allHonors = list) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            combine(
                combine(
                    userRepository.observeUser(scoutId),
                    levelRepository.observeLevels(),
                    levelRepository.observeCompletions(scoutId),
                    levelRepository.observeAllProgressByUser(scoutId),
                ) { user, levels, levelCompletions, levelProgress ->
                    ScoutBase(user, levels, levelCompletions, levelProgress)
                },
                combine(
                    honorRepository.observeCompletions(scoutId),
                    honorRepository.observeAllProgressByUser(scoutId),
                    levelRepository.observeAllRequirements(),
                    honorRepository.observeAllRequirements(),
                    honorRepository.observeAllHonors(),
                ) { honorCompletions, honorProgress, allLevelReqs, allHonorReqs, allHonors ->
                    ScoutHonors(honorCompletions, honorProgress, allLevelReqs, allHonorReqs, allHonors)
                },
            ) { base, honors ->
                val user = base.user
                val levels = base.levels
                val levelCompletions = base.levelCompletions
                val levelProgress = base.levelProgress
                val honorCompletions = honors.honorCompletions
                val honorProgress = honors.honorProgress
                val allLevelReqs = honors.allLevelReqs
                val allHonorReqs = honors.allHonorReqs
                val allHonors = honors.allHonors

                if (user == null) return@combine null

                val levelOrderById = levels.associate { it.id to it.order }
                val levelNameById = levels.associate { it.id to it.name }
                val completedLevelIds = levelCompletions.map { it.levelId }.toSet()

                val completedLevels = levelCompletions
                    .sortedBy { levelOrderById[it.levelId] ?: Int.MAX_VALUE }
                    .map { lc ->
                        CompletedLevelSummary(
                            levelId = lc.levelId,
                            levelName = levelNameById[lc.levelId] ?: lc.levelId,
                            completedAt = lc.completedAt,
                        )
                    }

                val currentLevel = levels.firstOrNull { it.id !in completedLevelIds }
                val currentLevelId = currentLevel?.id
                val reqsByLevel = allLevelReqs.groupBy { it.levelId }
                val reqsByHonor = allHonorReqs.groupBy { it.honorId }
                val honorsById = allHonors.associateBy { it.id }

                val levelReqs = if (currentLevelId != null) reqsByLevel[currentLevelId].orEmpty() else emptyList()
                val levelProgressByReq = levelProgress.associateBy { it.requirementId }
                val levelReqStatuses = levelReqs.sortedBy { it.order }.map { req ->
                    val p = levelProgressByReq[req.id]
                    RequirementStatus(
                        id = req.id,
                        text = req.text,
                        order = req.order,
                        isAdvanced = req.isAdvanced,
                        approved = p?.status == "approved",
                        submitted = p?.status == "submitted",
                        approvedAt = p?.approvedAt,
                        mediaUrls = p?.mediaUrls ?: emptyList(),
                    )
                }

                val completedHonorIds = honorCompletions.map { it.honorId }.toSet()
                val inProgressHonorIds = honorProgress.map { it.honorId }
                    .distinct()
                    .filter { it !in completedHonorIds }

                val honorDetails = mutableListOf<HonorProgressDetail>()
                (inProgressHonorIds + completedHonorIds).forEach { honorId ->
                    val honor = honorsById[honorId]
                    val reqs = reqsByHonor[honorId].orEmpty()
                    val progress = honorProgress.filter { it.honorId == honorId }
                    val progressByReq = progress.associateBy { it.requirementId }
                    val reqStatuses = reqs.sortedBy { it.order }.map { req ->
                        val p = progressByReq[req.id]
                        RequirementStatus(
                            id = req.id,
                            text = req.text,
                            order = req.order,
                            isAdvanced = false,
                            approved = p?.status == "approved",
                            submitted = p?.status == "submitted",
                            approvedAt = p?.approvedAt,
                            mediaUrls = p?.mediaUrls ?: emptyList(),
                        )
                    }
                    val approved = reqStatuses.count { it.approved }
                    val total = reqStatuses.size
                    val percent = if (total > 0) (approved * 100) / total else 0
                    val completedAt = honorCompletions.firstOrNull { it.honorId == honorId }?.completedAt
                    honorDetails.add(
                        HonorProgressDetail(
                            honorId = honorId,
                            honorName = honor?.name ?: honorId,
                            imageUrl = honor?.previewUrl ?: honor?.imageUrl,
                            requirementsTotal = total,
                            requirementsApproved = approved,
                            percent = percent,
                            requirements = reqStatuses,
                            completedAt = completedAt,
                        )
                    )
                }

                ScoutDetail(
                    user = user,
                    completedLevels = completedLevels,
                    currentLevelId = currentLevelId,
                    currentLevelName = currentLevel?.name,
                    levelRequirements = levelReqStatuses,
                    honorsInProgress = honorDetails.filter { it.completedAt == null }.sortedBy { it.honorName },
                    honorsCompleted = honorDetails.filter { it.completedAt != null }.sortedBy { it.honorName },
                )
            }.collect { detail ->
                _state.update { it.copy(isLoading = false, detail = detail) }
            }
        }
    }

    fun openAwardDialog() = _state.update { it.copy(showAwardDialog = true) }
    fun dismissAwardDialog() = _state.update { it.copy(showAwardDialog = false) }
    fun clearMessages() = _state.update { it.copy(savedMessage = null, errorMessage = null) }

    fun awardSpecialization(honorId: String) {
        val adminId = sessionManager.getUserId() ?: return
        val now = Date()
        _state.update { it.copy(showAwardDialog = false) }

        viewModelScope.launch {
            try {
                // 1. HonorCompletion
                honorRepository.upsertCompletion(
                    HonorCompletionEntity(
                        userId = scoutId,
                        honorId = honorId,
                        completedAt = now,
                        approvedBy = adminId,
                    )
                )

                // 2. Digital Uniform — добавить нашивку
                val uniform = achievementRepository.getUniform(scoutId)
                    ?: com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity(
                        userId = scoutId,
                        shirtColor = "",
                        tieColor = "",
                        earnedBadges = emptyList(),
                        earnedChevrons = emptyList(),
                        specialMarks = emptyList(),
                        updatedAt = now,
                    )

                val newBadge = com.pathfinder.hub.data.local.entity.gamification.BadgeItem(
                    honorId = honorId,
                    position = uniform.earnedBadges.size,
                    earnedAt = now,
                )
                val updated = uniform.copy(
                    earnedBadges = uniform.earnedBadges + newBadge,
                    updatedAt = now,
                    pendingSync = true,
                )
                achievementRepository.upsertUniformWithSync(updated)

                _state.update { it.copy(savedMessage = "Специализация выдана") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка выдачи") }
            }
        }
    }
}
