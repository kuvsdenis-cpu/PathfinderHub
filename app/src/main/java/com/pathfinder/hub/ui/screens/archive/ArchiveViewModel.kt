package com.pathfinder.hub.ui.screens.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.ModerationRepository
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.QrStationRepository
import com.pathfinder.hub.data.repository.ReportRepository
import com.pathfinder.hub.data.repository.TaskRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchiveUiState(
    val isLoading: Boolean = true,
    val activeTab: Int = 0,
    val archivedEvents: List<EventEntity> = emptyList(),
    val archivedTasks: List<com.pathfinder.hub.data.local.entity.planning.TaskEntity> = emptyList(),
    val archivedHomework: List<com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity> = emptyList(),
    val archivedComments: List<com.pathfinder.hub.data.local.entity.planning.CommentEntity> = emptyList(),
    val archivedQuests: List<com.pathfinder.hub.data.local.entity.qr.QrQuestEntity> = emptyList(),
    val archivedStations: List<com.pathfinder.hub.data.local.entity.qr.QrStationEntity> = emptyList(),
    val archivedReports: List<com.pathfinder.hub.data.local.entity.reports.ReportEntity> = emptyList(),
    val archivedGroups: List<CustomGroupEntity> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val taskRepository: TaskRepository,
    private val achievementRepository: AchievementRepository,
    private val moderationRepository: ModerationRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val qrStationRepository: QrStationRepository,
    private val reportRepository: ReportRepository,
    private val customGroupRepository: CustomGroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            observeArchives(uid)
        }
    }

    private fun observeArchives(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                archivedEvents = emptyList(),
                                archivedTasks = emptyList(),
                                archivedHomework = emptyList(),
                                archivedComments = emptyList(),
                                archivedQuests = emptyList(),
                                archivedStations = emptyList(),
                                archivedReports = emptyList(),
                                archivedGroups = emptyList(),
                            )
                        }
                        return@collectLatest
                    }
                    _state.update { it.copy(isLoading = false) }

                    launch {
                        eventRepository.observeArchivedByClub(clubId).collect { list ->
                            _state.update { it.copy(archivedEvents = list) }
                        }
                    }
                    launch {
                        taskRepository.observeArchivedClubTasks(clubId).collect { list ->
                            _state.update { it.copy(archivedTasks = list) }
                        }
                    }
                    launch {
                        achievementRepository.observeArchivedHomeworkByClub(clubId).collect { list ->
                            _state.update { it.copy(archivedHomework = list) }
                        }
                    }
                    launch {
                        moderationRepository.observeArchivedCommentsByClub(clubId).collect { list ->
                            _state.update { it.copy(archivedComments = list) }
                        }
                    }
                    launch {
                        qrQuestRepository.observeArchivedQuests(clubId).collect { list ->
                            _state.update { it.copy(archivedQuests = list) }
                        }
                    }
                    launch {
                        qrStationRepository.observeArchivedStations(clubId).collect { list ->
                            _state.update { it.copy(archivedStations = list) }
                        }
                    }
                    launch {
                        reportRepository.observeArchivedReportsByClub(clubId).collect { list ->
                            _state.update { it.copy(archivedReports = list) }
                        }
                    }
                    launch {
                        customGroupRepository.observeArchivedByClub(clubId).collect { list ->
                            _state.update { it.copy(archivedGroups = list) }
                        }
                    }
                }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(activeTab = idx) }

    fun restoreEvent(id: String) {
        viewModelScope.launch { eventRepository.restoreEvent(id) }
    }
    fun restoreTask(id: String) {
        viewModelScope.launch { taskRepository.restoreTask(id) }
    }
    fun restoreHomework(id: String) {
        viewModelScope.launch { achievementRepository.restoreHomework(id) }
    }
    fun restoreComment(id: String) {
        viewModelScope.launch { moderationRepository.restoreComment(id) }
    }
    fun restoreQuest(id: String) {
        viewModelScope.launch { qrQuestRepository.restoreQuest(id) }
    }
    fun restoreStation(id: String) {
        viewModelScope.launch { qrStationRepository.restoreStation(id) }
    }
    fun restoreReport(id: String) {
        viewModelScope.launch { reportRepository.restoreReport(id) }
    }
    fun restoreGroup(id: String) {
        viewModelScope.launch { customGroupRepository.restore(id) }
    }
}
