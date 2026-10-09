package com.pathfinder.hub.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.dao.HonorDao
import com.pathfinder.hub.data.local.dao.LevelDao
import com.pathfinder.hub.data.local.dao.ModerationDao
import com.pathfinder.hub.data.repository.QrStationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityScale(
    val label: String,
    val count: Int,
    val level: Int,
    val nextThreshold: Int?,
)

data class ActivityUiState(
    val isLoading: Boolean = true,
    val requirements: ActivityScale = ActivityScale("Требования", 0, 1, 5),
    val messages: ActivityScale = ActivityScale("Сообщения", 0, 1, 5),
    val visits: ActivityScale = ActivityScale("Посещения", 0, 1, 1),
    val overallLevel: Int = 1,
    val overallPercent: Float = 0f,
    val errorMessage: String? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val levelDao: LevelDao,
    private val honorDao: HonorDao,
    private val moderationDao: ModerationDao,
    private val qrRepository: QrStationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            try {
                val levelReq = try { levelDao.countApprovedRequirementsByUser(userId) } catch (_: Exception) { 0 }
                val honorReq = try { honorDao.countApprovedRequirementsByUser(userId) } catch (_: Exception) { 0 }
                val reqTotal = levelReq + honorReq

                val msgTotal = try { moderationDao.countUserComments(userId) } catch (_: Exception) { 0 }

                val visitsTotal = try { qrRepository.countVerifiedVisits(userId) } catch (_: Exception) { 0 }

                val reqScale = scaleFor(reqTotal, intArrayOf(0, 5, 20, 50, 100), "Требования")
                val msgScale = scaleFor(msgTotal, intArrayOf(0, 5, 20, 50, 100), "Сообщения")
                val visitScale = scaleFor(visitsTotal, intArrayOf(0, 1, 3, 10, 25), "Посещения")

                val overall = ((reqScale.level + msgScale.level + visitScale.level) / 3f)
                val overallPercent = (overall - 1f) / 4f

                _state.update {
                    it.copy(
                        isLoading = false,
                        requirements = reqScale,
                        messages = msgScale,
                        visits = visitScale,
                        overallLevel = overall.toInt().coerceIn(1, 5),
                        overallPercent = overallPercent.coerceIn(0f, 1f),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * thresholds: массив [0, 5, 20, 50, 100]. Возвращает уровень 1..5.
     */
    private fun scaleFor(count: Int, thresholds: IntArray, label: String): ActivityScale {
        var level = 1
        for (i in thresholds.indices) {
            if (count >= thresholds[i]) level = i + 1
        }
        level = level.coerceIn(1, 5)
        val next = if (level < 5) thresholds[level] else null
        return ActivityScale(label, count, level, next)
    }
}
