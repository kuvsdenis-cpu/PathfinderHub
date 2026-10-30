package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.RoleDefaultPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ClubMembersUiState(
    val isLoading: Boolean = true,
    val members: List<UserEntity> = emptyList(),
    val currentUserId: String? = null,
    val currentUserRole: String = "",
    val clubId: String? = null,
    val pendingDmId: String? = null,
    val pendingOtherUid: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val linkDialogMember: UserEntity? = null,
    val linkDialogCandidates: List<UserEntity> = emptyList(),
    val linkDialogSelected: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClubMembersViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val chatRepo: ChatRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClubMembersUiState())
    val state: StateFlow<ClubMembersUiState> = _state.asStateFlow()

    init {
        observeMembers()
    }

    private fun observeMembers() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
            return
        }

        viewModelScope.launch {
            // Подписка на текущего user (role / clubId)
            launch {
                userRepository.observeUser(userId).collect { me ->
                    if (me == null) return@collect
                    _state.update {
                        it.copy(
                            currentUserId = userId,
                            currentUserRole = me.role,
                        )
                    }
                }
            }

            // Подписка на members: clubId → перезагрузка
            userRepository.observeUser(userId)
                .map { it?.clubId }
                .distinctUntilChanged()
                .flatMapLatest { clubId ->
                    if (clubId.isNullOrBlank()) flowOf(emptyList())
                    else userRepository.observeClubMembersByRole(clubId, "teen")
                }
                .collect { members ->
                    _state.update { it.copy(members = members, isLoading = false) }
                }
        }
    }

    fun changeRole(userId: String, newRole: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId) ?: return@launch
                if (user.role == newRole) return@launch

                // При смене роли — ставим дефолтный набор прав.
                // Owner клуба (isOwnerClub=true) — права не важны, у него всё.
                // Если owner уже выдал права вручную — они перезапишутся дефолтом.
                // Это ожидаемое поведение: смена роли = новый набор по умолчанию.
                val newPerms = if (user.isOwnerClub) {
                    user.directorPermissions
                } else {
                    RoleDefaultPermissions.forRole(newRole)
                }
                userRepository.update(user.copy(
                    role = newRole,
                    directorPermissions = newPerms,
                ))
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка смены роли") }
            }
        }
    }

    fun suspendUser(userId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId) ?: return@launch
                userRepository.update(user.copy(status = "suspended"))
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка приостановки") }
            }
        }
    }

    fun removeUser(userId: String) {
        viewModelScope.launch {
            try {
                userRepository.requestDeletion(userId, "deleted", System.currentTimeMillis())
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun openDM(otherUserId: String) {
        val uid = _state.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                val me = userRepository.getUser(uid) ?: return@launch
                val clubId = me.clubId ?: return@launch
                val r = chatRepo.openDM(clubId, uid, otherUserId)
                r.fold(
                    onSuccess = { dmId ->
                        _state.update { it.copy(pendingDmId = dmId, pendingOtherUid = otherUserId) }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(errorMessage = e.message) }
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearPendingDM() {
        _state.update { it.copy(pendingDmId = null, pendingOtherUid = null) }
    }

    fun openLinkDialog(member: UserEntity) {
        val targetRole = when (member.role) {
            "parent" -> "teen"
            "teen" -> "parent"
            else -> return
        }
        val candidates = _state.value.members
            .filter { it.role == targetRole && it.id != member.id }
        _state.update {
            it.copy(
                linkDialogMember = member,
                linkDialogCandidates = candidates,
                linkDialogSelected = null,
            )
        }
    }

    fun closeLinkDialog() {
        _state.update {
            it.copy(
                linkDialogMember = null,
                linkDialogCandidates = emptyList(),
                linkDialogSelected = null,
            )
        }
    }

    fun onLinkCandidateSelected(userId: String) {
        _state.update { it.copy(linkDialogSelected = userId) }
    }

    fun confirmLink() {
        val s = _state.value
        val member = s.linkDialogMember ?: return
        val selected = s.linkDialogSelected ?: return
        val clubId = s.clubId ?: return
        val directorId = s.currentUserId ?: return

        val parentId: String
        val childId: String
        if (member.role == "parent") {
            parentId = member.id
            childId = selected
        } else {
            parentId = selected
            childId = member.id
        }

        viewModelScope.launch {
            try {
                parentRepository.upsertLink(
                    ParentChildLinkEntity(
                        parentId = parentId,
                        childId = childId,
                        clubId = clubId,
                        relationship = "parent",
                        status = "active",
                        linkedBy = directorId,
                        linkedAt = Date(),
                        revokedAt = null,
                        revokedReason = null,
                    )
                )
                _state.update {
                    it.copy(
                        linkDialogMember = null,
                        linkDialogCandidates = emptyList(),
                        linkDialogSelected = null,
                        infoMessage = "Родитель привязан к ребёнку",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка привязки") }
            }
        }
    }

    fun clearInfo() = _state.update { it.copy(infoMessage = null) }
}
