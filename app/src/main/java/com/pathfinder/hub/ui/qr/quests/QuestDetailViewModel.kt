package com.pathfinder.hub.ui.qr.quests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.QrStationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Одна станция квеста с её состоянием для конкретного teen.
 *   index — позиция в квесте (1..N)
 *   isCompleted — пройдена ли
 *   isCurrent — идёт сейчас (следующая к прохождению)
 *   isLocked — ещё закрыта (не показываем название)
 */
data class QuestStep(
    val index: Int,
    val station: QrStationEntity?,
    val isCompleted: Boolean,
    val isCurrent: Boolean,
    val isLocked: Boolean,
)

data class QuestDetailUiState(
    val isLoading: Boolean = true,
    val quest: QrQuestEntity? = null,
    val steps: List<QuestStep> = emptyList(),
    val completedCount: Int = 0,
    val totalSteps: Int = 0,
    val errorMessage: String? = null,
)

@HiltViewModel
class QuestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val qrQuestRepository: QrQuestRepository,
    private val qrStationRepository: QrStationRepository,
) : ViewModel() {

    private val questId: String = savedStateHandle.get<String>("questId") ?: ""

    private val _state = MutableStateFlow(QuestDetailUiState())
    val state: StateFlow<QuestDetailUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid.isNullOrBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(uid: String) {
        viewModelScope.launch {
            val quest = qrQuestRepository.getQuest(questId)
            if (quest == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Квест не найден") }
                return@launch
            }
            _state.update { it.copy(quest = quest) }

            combine(
                qrQuestRepository.observeQuestStations(questId),
                qrQuestRepository.observeProgress(questId, uid),
                qrStationRepository.observeAllStations(quest.clubId),
            ) { links, progress, stations ->
                val sorted = links.sortedBy { it.stepIndex }
                val completed = progress?.lastCompletedStepIndex ?: 0
                val currentIdx = completed + 1

                val steps = sorted.mapIndexed { i, link ->
                    val idx = i + 1
                    val st = stations.find { it.id == link.stationId }
                    val isDone = idx <= completed
                    val isCurrent = idx == currentIdx
                    val isLocked = idx > currentIdx
                    QuestStep(
                        index = idx,
                        station = if (isLocked) null else st,
                        isCompleted = isDone,
                        isCurrent = isCurrent,
                        isLocked = isLocked,
                    )
                }
                Triple(quest, steps, sorted.size)
            }.collect { (q, steps, total) ->
                val done = steps.count { it.isCompleted }
                _state.update {
                    it.copy(
                        isLoading = false,
                        quest = q,
                        steps = steps,
                        completedCount = done,
                        totalSteps = total,
                    )
                }
            }
        }
    }
}
