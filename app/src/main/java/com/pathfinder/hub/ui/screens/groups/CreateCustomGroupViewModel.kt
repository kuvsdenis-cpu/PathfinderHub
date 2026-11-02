package com.pathfinder.hub.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.local.entity.groups.CustomGroupRule
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateCustomGroupUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val groupId: String = "",
    val name: String = "",
    val description: String = "",

    // Правило
    val roles: Set<String> = emptySet(),
    val levelIds: Set<String> = emptySet(),
    val honorIds: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val ownerFilter: String = "any",  // "any" | "yes" | "no"

    // Справочники
    val allLevels: List<LevelEntity> = emptyList(),
    val allHonors: List<HonorEntity> = emptyList(),

    // Предпросмотр
    val previewCount: Int? = null,

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class CreateCustomGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val repo: CustomGroupRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
) : ViewModel() {

    private val incomingGroupId: String = savedStateHandle.get<String>("groupId") ?: "new"
    private val isNew: Boolean = incomingGroupId == "new" || incomingGroupId.isBlank()

    private val _state = MutableStateFlow(CreateCustomGroupUiState(isNew = isNew))
    val state: StateFlow<CreateCustomGroupUiState> = _state.asStateFlow()

    private var previewJob: Job? = null
    private var clubId: String = ""

    init {
        viewModelScope.launch {
            val uid = sessionManager.getUserId()
            val me = uid?.let { userRepository.getUser(it) }
            clubId = me?.clubId ?: ""

            // Справочники
            launch {
                levelRepository.observeLevels().collect { l ->
                    _state.update { it.copy(allLevels = l) }
                }
            }
            launch {
                honorRepository.observeAllHonors().collect { h ->
                    _state.update { it.copy(allHonors = h) }
                }
            }

            // Загрузка группы при редактировании
            if (!isNew) {
                val g = repo.getById(incomingGroupId)
                if (g != null) {
                    val r = repo.decodeRule(g)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            groupId = g.id,
                            name = g.name,
                            description = g.description,
                            roles = r.roles.toSet(),
                            levelIds = r.levelIds.toSet(),
                            honorIds = r.honorIds.toSet(),
                            statuses = r.statuses.toSet(),
                            ownerFilter = when (r.isOwnerClub) {
                                null -> "any"; true -> "yes"; false -> "no"
                            },
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, errorMessage = "Группа не найдена") }
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }

            schedulePreview()
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }

    fun toggleRole(role: String) = _state.update {
        it.copy(roles = if (role in it.roles) it.roles - role else it.roles + role)
    }.also { schedulePreview() }

    fun toggleLevel(id: String) = _state.update {
        it.copy(levelIds = if (id in it.levelIds) it.levelIds - id else it.levelIds + id)
    }.also { schedulePreview() }

    fun toggleHonor(id: String) = _state.update {
        it.copy(honorIds = if (id in it.honorIds) it.honorIds - id else it.honorIds + id)
    }.also { schedulePreview() }

    fun toggleStatus(s: String) = _state.update {
        it.copy(statuses = if (s in it.statuses) it.statuses - s else it.statuses + s)
    }.also { schedulePreview() }

    fun setOwnerFilter(v: String) = _state.update { it.copy(ownerFilter = v) }.also { schedulePreview() }

    private fun currentRule(): CustomGroupRule {
        val s = _state.value
        return CustomGroupRule(
            roles = s.roles.toList(),
            levelIds = s.levelIds.toList(),
            honorIds = s.honorIds.toList(),
            statuses = s.statuses.toList(),
            isOwnerClub = when (s.ownerFilter) {
                "yes" -> true
                "no" -> false
                else -> null
            },
        )
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _state.update { it.copy(previewCount = null) }
            delay(300)  // debounce
            if (clubId.isBlank()) return@launch
            try {
                val ids = repo.resolveMembersFromRule(clubId, currentRule())
                _state.update { it.copy(previewCount = ids.size) }
            } catch (_: Exception) {
                _state.update { it.copy(previewCount = 0) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        val uid = sessionManager.getUserId() ?: return
        if (clubId.isBlank()) {
            _state.update { it.copy(errorMessage = "Нет клуба") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val now = Date()
                val entity = CustomGroupEntity(
                    id = if (isNew) UUID.randomUUID().toString() else s.groupId,
                    clubId = clubId,
                    name = s.name.trim(),
                    description = s.description.trim(),
                    createdBy = uid,
                    createdAt = now,
                    updatedAt = now,
                    ruleJson = "{}",  // перезапишется в repo.upsert
                )
                repo.upsert(entity, currentRule())
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }

    fun deleteGroup() {
        val s = _state.value
        if (s.isNew) return
        viewModelScope.launch {
            try {
                repo.delete(s.groupId)
                _state.update { it.copy(isSaved = true, infoMessage = "Удалено") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
