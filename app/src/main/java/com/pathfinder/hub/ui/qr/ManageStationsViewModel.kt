package com.pathfinder.hub.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestWithStations(
    val quest: com.pathfinder.hub.data.local.entity.qr.QrQuestEntity,
    val stations: List<QrStationEntity>,
)

data class ManageStationsUiState(
    val isLoading: Boolean = true,
    val activeTab: Int = 0,
    val standalone: List<QrStationEntity> = emptyList(),
    val inQuests: List<QrStationEntity> = emptyList(),
    val questsWithStations: List<QuestWithStations> = emptyList(),
    val canArchive: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ManageStationsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrRepository: QrStationRepository,
    private val qrQuestRepository: com.pathfinder.hub.data.repository.QrQuestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageStationsUiState())
    val state: StateFlow<ManageStationsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                val canArchive = com.pathfinder.hub.domain.permissions.PermissionChecker
                    .canArchive(user)
                _state.update { it.copy(canArchive = canArchive) }
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }

                launch {
                    qrRepository.observeStandaloneStations(clubId).collect { list ->
                        _state.update { it.copy(isLoading = false, standalone = list) }
                    }
                }
                launch {
                    qrRepository.observeStationsInQuests(clubId).collect { list ->
                        _state.update { it.copy(inQuests = list) }
                    }
                }
                // Группировка станций по квестам
                launch {
                    kotlinx.coroutines.flow.combine(
                        qrQuestRepository.observeAllQuests(clubId),
                        qrRepository.observeStationsInQuests(clubId),
                        qrQuestRepository.observeAllLinksForClub(clubId),
                    ) { quests, stations, links ->
                        quests
                            .filter { it.status == "active" && it.archivedAt == null }
                            .map { q ->
                                QuestWithStations(
                                    quest = q,
                                    stations = links
                                        .filter { it.questId == q.id }
                                        .sortedBy { it.stepIndex }
                                        .mapNotNull { l -> stations.find { s -> s.id == l.stationId } },
                                )
                            }
                            .filter { it.stations.isNotEmpty() }
                    }.collect { list ->
                        _state.update { it.copy(questsWithStations = list) }
                    }
                }
            }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(activeTab = idx) }

    fun deleteStation(id: String) {
        viewModelScope.launch {
            try { qrRepository.deleteStation(id) }
            catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun archiveStation(id: String) {
        viewModelScope.launch {
            qrRepository.archiveStation(id).fold(
                onSuccess = {
                    _state.update { it.copy(infoMessage = "Станция в архиве") }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message) }
                },
            )
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
    fun clearInfo() = _state.update { it.copy(infoMessage = null) }
}
