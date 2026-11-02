package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class HomeworkGroup(
    val key: String,
    val title: String,
    val description: String,
    val dueDate: Date,
    val createdAt: Date,
    val items: List<HomeworkEntity>,
) {
    val pendingCount: Int get() = items.count { it.status == "done" }
    val reviewedCount: Int get() = items.count { it.status == "approved" || it.status == "rejected" }
    val totalCount: Int get() = items.size
}

data class StaffHomeworkUiState(
    val isLoading: Boolean = true,
    val groups: List<HomeworkGroup> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val selectedGroup: HomeworkGroup? = null,
    val errorMessage: String? = null,
    val message: String? = null,
)

@HiltViewModel
class StaffHomeworkViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffHomeworkUiState())
    val state: StateFlow<StaffHomeworkUiState> = _state.asStateFlow()

    private var currentClubId: String? = null

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeHomework(uid)
        }
    }

    private fun observeHomework(userId: String) {
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(isLoading = false, groups = emptyList()) }
                        return@collectLatest
                    }
                    currentClubId = clubId
                    achievementRepository.observeActiveHomeworkByClub(clubId).collect { list ->
                        val groups = groupHomework(list)
                        resolveChildNames(groups, list)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                groups = groups,
                                selectedGroup = it.selectedGroup?.let { old ->
                                    groups.find { g -> g.key == old.key }
                                },
                            )
                        }
                    }
                }
        }
    }

    private fun groupHomework(items: List<HomeworkEntity>): List<HomeworkGroup> {
        return items
            .groupBy { "${it.title}|${it.createdAt.time}" }
            .map { (key, groupItems) ->
                val first = groupItems.first()
                HomeworkGroup(
                    key = key,
                    title = first.title,
                    description = first.description,
                    dueDate = first.dueDate,
                    createdAt = first.createdAt,
                    items = groupItems.sortedBy { it.childId },
                )
            }
            .sortedByDescending { it.createdAt }
    }

    private suspend fun resolveChildNames(
        groups: List<HomeworkGroup>,
        items: List<HomeworkEntity>,
    ) {
        val childIds = items.map { it.childId }.distinct()
        val names = mutableMapOf<String, String>()
        childIds.forEach { id ->
            val u = userRepository.getUser(id)
            if (u != null) {
                names[id] = "${u.firstName} ${u.lastName}".trim()
            }
        }
        _state.update { it.copy(childNames = names) }
    }

    fun openGroup(group: HomeworkGroup) {
        _state.update { it.copy(selectedGroup = group) }
    }

    fun closeGroup() {
        _state.update { it.copy(selectedGroup = null) }
    }

    fun approve(item: HomeworkEntity) {
        viewModelScope.launch {
            try {
                achievementRepository.approveHomework(item)
                _state.update { it.copy(message = "Одобрено: " + item.title) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun reject(item: HomeworkEntity) {
        viewModelScope.launch {
            try {
                achievementRepository.rejectHomework(item)
                _state.update { it.copy(message = "Возвращено: " + item.title) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, errorMessage = null) }
}
