package com.pathfinder.hub.ui.screens.home.director.staff

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

data class StaffUiState(
    val isLoading: Boolean = true,
    val directors: List<UserEntity> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffUiState())
    val state: StateFlow<StaffUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            _state.update { it.copy(currentUserId = uid) }
            load(uid)
        }
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            // «Сотрудники» = co-director + instructor + secretary.
            // Owner (isOwnerClub == true) НЕ показывается — он сам себя видит на главной.
            userRepository
                .observeClubMembersByRoles(
                    clubId,
                    listOf("director", "instructor", "secretary"),
                )
                .collect { list ->
                    val staff = list.filter { user ->
                        when (user.role) {
                            "director" -> !user.isOwnerClub   // только co-director
                            "instructor", "secretary" -> true
                            else -> false
                        }
                    }
                    _state.update { it.copy(isLoading = false, directors = staff) }
                }
        }
    }
}
