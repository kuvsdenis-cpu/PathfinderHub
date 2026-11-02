package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM для вкладок клубов на экранах директора.
 *
 * Читает user.clubId / user.clubIds из Room.
 * switchClub → userRepository.setActiveClub → user.clubId обновляется →
 * все экраны, читающие observeUser, автоматически перезагружаются.
 */
@HiltViewModel
class ClubContextViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
) : ViewModel() {

    data class State(
        val clubs: List<ClubEntity> = emptyList(),
        val selectedClubId: String? = null,
        val currentUser: UserEntity? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        observeClubs()
    }

    private fun observeClubs() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user == null) return@collect
                val ids = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
                val clubs = ids.mapNotNull { id ->
                    try {
                        clubRepository.getClub(id)?.takeIf { it.deletedAt == null }
                    } catch (_: Exception) { null }
                }
                val activeId = user.clubId ?: clubs.firstOrNull()?.id
                android.util.Log.d(
                    "ClubContextVM",
                    "user.clubIds=${user.clubIds}, user.clubId=${user.clubId}, " +
                    "resolved clubs=${clubs.map { it.id }}, activeId=$activeId"
                )
                _state.update {
                    State(
                        clubs = clubs,
                        selectedClubId = activeId,
                        currentUser = user,
                    )
                }
            }
        }
    }

    fun switchClub(clubId: String) {
        val userId = sessionManager.getUserId() ?: return
        if (_state.value.selectedClubId == clubId) return
        viewModelScope.launch {
            userRepository.setActiveClub(userId, clubId)
        }
    }
}
