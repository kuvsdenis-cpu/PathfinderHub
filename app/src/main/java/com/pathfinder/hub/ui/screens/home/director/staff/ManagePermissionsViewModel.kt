package com.pathfinder.hub.ui.screens.home.director.staff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagePermissionsUiState(
    val isLoading: Boolean = true,
    val target: UserEntity? = null,
    val selected: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

@HiltViewModel
class ManagePermissionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val targetId: String = savedStateHandle.get<String>("userId") ?: ""

    private val _state = MutableStateFlow(ManagePermissionsUiState())
    val state: StateFlow<ManagePermissionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val myId = sessionManager.getUserId()
        if (myId == null || targetId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Неверный запрос") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(myId)
            if (me == null || !me.isOwnerClub) {
                _state.update { it.copy(isLoading = false, errorMessage = "Только владелец клуба может менять права") }
                return@launch
            }
            userRepository.observeUser(targetId).collect { user ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        target = user,
                        selected = user?.directorPermissions?.toSet() ?: emptySet(),
                    )
                }
            }
        }
    }

    fun toggle(perm: String) {
        _state.update {
            val cur = it.selected
            val next = if (perm in cur) cur - perm else cur + perm
            it.copy(selected = next, savedMessage = null)
        }
    }

    fun save() {
        val uid = sessionManager.getUserId() ?: return
        val target = _state.value.target ?: return
        if (target.isOwnerClub) {
            _state.update { it.copy(errorMessage = "Владелец клуба всегда имеет полные права") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                userRepository.update(
                    target.copy(directorPermissions = _state.value.selected.toList())
                )
                _state.update { it.copy(isSaving = false, savedMessage = "Сохранено") }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(savedMessage = null, errorMessage = null) }
}
