package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.FcmTokenRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildSummary(
    val child: UserEntity?,
    val link: ParentChildLinkEntity,
    val completedLevels: Int,
    val earnedAchievements: Int,
)

data class ParentHomeUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val children: List<ChildSummary> = emptyList(),
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
    private val levelRepository: LevelRepository,
    private val achievementRepository: AchievementRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentHomeUiState())
    val state: StateFlow<ParentHomeUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            observeUser(uid)
            observeChildren(uid)
        }
    }

    private fun observeUser(uid: String) {
        viewModelScope.launch {
            userRepository.observeUser(uid).collect { u ->
                _state.update { it.copy(user = u, isLoading = false) }
            }
        }
    }

    private fun observeChildren(parentId: String) {
        viewModelScope.launch {
            parentRepository.observeChildren(parentId).collect { links ->
                val active = links.filter { it.status == "active" }
                val summaries = active.map { link ->
                    val child = userRepository.getUser(link.childId)
                    val completions = try {
                        levelRepository.observeCompletions(link.childId)
                    } catch (_: Exception) { null }
                    // Для счётчиков используем snapshot-once
                    val levels = try {
                        levelRepository.observeCompletions(link.childId)
                    } catch (_: Exception) { null }
                    ChildSummary(
                        child = child,
                        link = link,
                        completedLevels = 0,   // подтянем отдельным flow ниже
                        earnedAchievements = 0,
                    )
                }
                _state.update { it.copy(children = summaries, isLoading = false) }

                // Подписки на счётчики каждого ребёнка
                active.forEach { link ->
                    launch {
                        levelRepository.observeCompletions(link.childId).collect { lc ->
                            _state.update { s ->
                                s.copy(children = s.children.map { cs ->
                                    if (cs.link.childId == link.childId)
                                        cs.copy(completedLevels = lc.size)
                                    else cs
                                })
                            }
                        }
                    }
                    launch {
                        achievementRepository.observeEarned(link.childId).collect { a ->
                            _state.update { s ->
                                s.copy(children = s.children.map { cs ->
                                    if (cs.link.childId == link.childId)
                                        cs.copy(earnedAchievements = a.size)
                                    else cs
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    fun logout() {
        val uid = sessionManager.getUserId()
        sessionManager.clear()
        _state.update { it.copy(loggedOut = true) }
        viewModelScope.launch {
            try { authRepository.signOut() } catch (_: Exception) {}
            if (uid != null) {
                try { fcmTokenRepository.removeCurrentToken(uid) } catch (_: Exception) {}
            }
        }
    }
}
