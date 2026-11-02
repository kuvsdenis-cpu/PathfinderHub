package com.pathfinder.hub.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.repository.CustomGroupRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionChecker
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

data class ManageCustomGroupsUiState(
    val isLoading: Boolean = true,
    val activeTab: Int = 0,
    val active: List<CustomGroupEntity> = emptyList(),
    val archived: List<CustomGroupEntity> = emptyList(),
    val canEdit: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManageCustomGroupsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val repo: CustomGroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageCustomGroupsUiState())
    val state: StateFlow<ManageCustomGroupsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observe(uid)
        }
    }

    private fun observe(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid).collect { me ->
                if (me == null) return@collect
                _state.update { it.copy(canEdit = PermissionChecker.hasPerm(me, "groups.manage")) }
            }
        }
        viewModelScope.launch {
            userRepository.observeUser(uid)
                .map { it?.clubId }
                .distinctUntilChanged()
                .collectLatest { clubId ->
                    if (clubId.isNullOrBlank()) {
                        _state.update { it.copy(isLoading = false, active = emptyList(), archived = emptyList()) }
                        return@collectLatest
                    }
                    launch {
                        repo.observeActiveByClub(clubId).collect { list ->
                            _state.update { it.copy(isLoading = false, active = list) }
                        }
                    }
                    launch {
                        repo.observeArchivedByClub(clubId).collect { list ->
                            _state.update { it.copy(archived = list) }
                        }
                    }
                }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(activeTab = idx) }

    fun archiveGroup(id: String) {
        viewModelScope.launch {
            try {
                repo.archive(id)
                _state.update { it.copy(infoMessage = "В архиве") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun restoreGroup(id: String) {
        viewModelScope.launch {
            try {
                repo.restore(id)
                _state.update { it.copy(infoMessage = "Восстановлено") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(infoMessage = null, errorMessage = null) }
}
