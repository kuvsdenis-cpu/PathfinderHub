package com.pathfinder.hub.ui.qr.quests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class QuestEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val questId: String? = null,
    val title: String = "",
    val description: String = "",
    val rewardPoints: String = "0",
    val rewardAchievementId: String = "",
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val selectedStationIds: List<String> = emptyList(),
    val allStations: List<QrStationEntity> = emptyList(),
    val isLoadingAllStations: Boolean = true,
    val showStationDialog: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class QuestEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val qrStationRepository: QrStationRepository,
) : ViewModel() {

    private val questIdArg: String? = savedStateHandle.get<String>("questId")
        ?.takeIf { it.isNotBlank() && it != "new" }

    private val _state = MutableStateFlow(QuestEditorUiState())
    val state: StateFlow<QuestEditorUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(uid: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }

            // Все станции клуба
            qrStationRepository.observeAllStations(clubId).collect { stations ->
                _state.update { it.copy(allStations = stations, isLoadingAllStations = false) }
            }
        }

        // Если edit — грузим квест
        if (questIdArg != null) {
            viewModelScope.launch {
                val quest = qrQuestRepository.getQuest(questIdArg)
                if (quest == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Квест не найден") }
                    return@launch
                }
                val links = qrQuestRepository.getQuestStations(questIdArg)
                _state.update {
                    it.copy(
                        isLoading = false,
                        isNew = false,
                        questId = quest.id,
                        title = quest.title,
                        description = quest.description,
                        rewardPoints = quest.rewardPoints.toString(),
                        rewardAchievementId = quest.rewardAchievementId ?: "",
                        dateStart = quest.dateStart,
                        dateEnd = quest.dateEnd,
                        selectedStationIds = links.sortedBy { l -> l.stepIndex }.map { l -> l.stationId },
                    )
                }
            }
        } else {
            _state.update { it.copy(isLoading = false, isNew = true) }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onRewardPoints(v: String) = _state.update { it.copy(rewardPoints = v.filter { c -> c.isDigit() }) }
    fun onRewardAchievement(v: String) = _state.update { it.copy(rewardAchievementId = v) }
    fun onDateStart(v: Date) = _state.update { it.copy(dateStart = v) }
    fun onDateEnd(v: Date) = _state.update { it.copy(dateEnd = v) }
    fun clearDates() = _state.update { it.copy(dateStart = null, dateEnd = null) }

    fun showStationDialog() = _state.update { it.copy(showStationDialog = true) }
    fun hideStationDialog() = _state.update { it.copy(showStationDialog = false) }

    fun toggleStation(stationId: String) {
        _state.update {
            val cur = it.selectedStationIds
            val next = if (stationId in cur) cur - stationId else cur + stationId
            it.copy(selectedStationIds = next)
        }
    }

    fun moveUp(stationId: String) {
        _state.update {
            val list = it.selectedStationIds.toMutableList()
            val i = list.indexOf(stationId)
            if (i > 0) {
                val tmp = list[i - 1]
                list[i - 1] = list[i]
                list[i] = tmp
            }
            it.copy(selectedStationIds = list)
        }
    }

    fun moveDown(stationId: String) {
        _state.update {
            val list = it.selectedStationIds.toMutableList()
            val i = list.indexOf(stationId)
            if (i >= 0 && i < list.size - 1) {
                val tmp = list[i + 1]
                list[i + 1] = list[i]
                list[i] = tmp
            }
            it.copy(selectedStationIds = list)
        }
    }

    fun save() {
        val s = _state.value
        val uid = sessionManager.getUserId() ?: return
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.selectedStationIds.isEmpty()) {
            _state.update { it.copy(errorMessage = "Добавьте хотя бы одну станцию") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isSaving = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                val id = s.questId ?: ("quest_" + UUID.randomUUID().toString().take(8))
                val now = Date()
                val entity = QrQuestEntity(
                    id = id,
                    clubId = clubId,
                    title = s.title.trim(),
                    description = s.description.trim(),
                    type = "linear",
                    rewardPoints = s.rewardPoints.toIntOrNull() ?: 0,
                    rewardAchievementId = s.rewardAchievementId.trim().ifBlank { null },
                    dateStart = s.dateStart,
                    dateEnd = s.dateEnd,
                    status = "active",
                    createdBy = uid,
                    createdAt = now,
                    updatedAt = now,
                    pendingSync = true,
                )
                qrQuestRepository.upsertQuest(entity)
                qrQuestRepository.setQuestStations(id, s.selectedStationIds)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
