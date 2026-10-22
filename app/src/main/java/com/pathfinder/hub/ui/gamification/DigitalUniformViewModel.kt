package com.pathfinder.hub.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementEntity
import kotlinx.coroutines.flow.first

data class ResolvedBadge(val honorId: String, val name: String, val imageUrl: String?, val earnedAt: java.util.Date)
data class ResolvedChevron(val levelId: String, val name: String, val icon: String?, val earnedAt: java.util.Date)
data class ResolvedMark(val achievementId: String, val name: String, val icon: String, val earnedAt: java.util.Date)

data class DigitalUniformUiState(
    val isLoading: Boolean = true,
    val uniform: com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity? = null,
    val resolvedBadges: List<ResolvedBadge> = emptyList(),
    val resolvedChevrons: List<ResolvedChevron> = emptyList(),
    val resolvedMarks: List<ResolvedMark> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class DigitalUniformViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val achievementRepository: AchievementRepository,
    private val honorRepository: HonorRepository,
    private val levelRepository: LevelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DigitalUniformUiState())
    val state: StateFlow<DigitalUniformUiState> = _state.asStateFlow()

    init {
        loadUniform()
    }

    private fun loadUniform() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
            return
        }

        viewModelScope.launch {
            achievementRepository.observeUniform(userId).collect { uniform ->
                _state.update { it.copy(isLoading = false, uniform = uniform) }
                if (uniform != null) resolveDetails(uniform)
            }
        }
    }

    private suspend fun resolveDetails(uniform: com.pathfinder.hub.data.local.entity.gamification.DigitalUniformEntity) {
        try {
            val badges = uniform.earnedBadges.map { b ->
                val h: HonorEntity? = try { honorRepository.getHonor(b.honorId) } catch (_: Exception) { null }
                ResolvedBadge(
                    honorId = b.honorId,
                    name = h?.name ?: "Специализация",
                    imageUrl = h?.previewUrl ?: h?.imageUrl,
                    earnedAt = b.earnedAt,
                )
            }
            val chevrons = uniform.earnedChevrons.map { cv ->
                val l: LevelEntity? = try { levelRepository.getLevel(cv.levelId) } catch (_: Exception) { null }
                ResolvedChevron(
                    levelId = cv.levelId,
                    name = l?.name ?: "Ступень",
                    icon = l?.icon,
                    earnedAt = cv.earnedAt,
                )
            }
            val all: List<AchievementEntity> = try { achievementRepository.observeAll().first() } catch (_: Exception) { emptyList() }
            val byId = all.associateBy { it.id }
            val marks = uniform.specialMarks.map { m ->
                val a = byId[m.achievementId]
                ResolvedMark(
                    achievementId = m.achievementId,
                    name = a?.name ?: "Достижение",
                    icon = a?.icon ?: m.icon,
                    earnedAt = m.earnedAt,
                )
            }
            _state.update {
                it.copy(
                    resolvedBadges = badges,
                    resolvedChevrons = chevrons,
                    resolvedMarks = marks,
                )
            }
        } catch (_: Exception) {}
    }
}
