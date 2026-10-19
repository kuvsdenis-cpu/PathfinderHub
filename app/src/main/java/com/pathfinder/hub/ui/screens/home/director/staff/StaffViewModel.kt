package com.pathfinder.hub.ui.screens.home.director.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.club.TransferClubOwnershipUseCase
import com.pathfinder.hub.domain.usecase.director.CreateInviteUseCase
import com.pathfinder.hub.domain.usecase.director.CreateTransferInviteUseCase
import com.pathfinder.hub.domain.usecase.club.TransferClubOwnershipByEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffUiState(
    val isLoading: Boolean = true,
    val directors: List<UserEntity> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
    // ---- Передача клуба ----
    val currentUser: UserEntity? = null,
    val showTransferDialog: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val transferTarget: UserEntity? = null,
    val transferError: String? = null,
    // ---- Пригласить нового директора ----
    val showInviteDialog: Boolean = false,
    val inviteEmail: String = "",
    val createdInviteCode: String? = null,
    val createdInviteExpiresAt: Long? = null,
    val inviteError: String? = null,
    // ---- Пригласить сотрудника (staff invite) ----
    val showStaffInviteDialog: Boolean = false,
    val staffInviteRole: String = "instructor",
    val staffInvitePermissions: Set<String> = emptySet(),
    val createdStaffInviteCode: String? = null,
    val staffInviteError: String? = null,
    // ---- Передача по email ----
    val showTransferByEmailDialog: Boolean = false,
    val transferEmail: String = "",
    val transferByEmailError: String? = null,
    val transferByEmailSuccess: Boolean = false,
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val transferClubOwnership: TransferClubOwnershipUseCase,
    private val createTransferInvite: CreateTransferInviteUseCase,
    private val createInvite: CreateInviteUseCase,
    private val transferByEmail: TransferClubOwnershipByEmailUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StaffUiState())
    val state: StateFlow<StaffUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            _state.update { it.copy(currentUserId = uid) }
            observeStaff(uid)
        }
    }

    private fun observeStaff(userId: String) {
        viewModelScope.launch {
            userRepository.observeUser(userId)
                .collectLatest { me ->
                    _state.update { it.copy(currentUser = me) }
                    val clubId = me?.clubId
                    if (clubId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                directors = emptyList(),
                                errorMessage = "Клуб не найден",
                            )
                        }
                        return@collectLatest
                    }
                    userRepository
                        .observeClubMembersByRoles(
                            clubId,
                            listOf("director", "instructor", "secretary"),
                        )
                        .collect { list ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    directors = list,
                                    errorMessage = null,
                                )
                            }
                        }
                }
        }
    }

    // ---------- Передача клуба ----------

    fun openTransferDialog() = _state.update { it.copy(showTransferDialog = true, transferError = null) }
    fun dismissTransferDialog() = _state.update { it.copy(showTransferDialog = false, transferTarget = null) }
    fun selectTarget(u: UserEntity) = _state.update { it.copy(transferTarget = u) }
    fun dismissConfirmDialog() = _state.update { it.copy(showConfirmDialog = false) }
    fun requestConfirm() {
        if (_state.value.transferTarget == null) return
        _state.update { it.copy(showConfirmDialog = true) }
    }

    // ---------- Пригласить нового директора ----------

    fun openInviteDialog() = _state.update {
        it.copy(showInviteDialog = true, inviteError = null,
            inviteEmail = "", createdInviteCode = null, createdInviteExpiresAt = null)
    }
    fun dismissInviteDialog() = _state.update {
        it.copy(showInviteDialog = false, inviteError = null,
            inviteEmail = "", createdInviteCode = null, createdInviteExpiresAt = null)
    }
    fun onInviteEmailChange(v: String) = _state.update { it.copy(inviteEmail = v) }

    fun createTransferInvite() {
        val me = _state.value.currentUser ?: return
        val clubId = me.clubId ?: return
        val email = _state.value.inviteEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            _state.update { it.copy(inviteError = "Введите корректный email") }
            return
        }

        viewModelScope.launch {
            val result = createTransferInvite(
                clubId = clubId,
                createdBy = me.id,
                userRole = me.role,
            )
            result.fold(
                onSuccess = { invite ->
                    _state.update {
                        it.copy(
                            inviteError = null,
                            createdInviteCode = invite.code,
                            createdInviteExpiresAt = invite.expiresAt.time,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(inviteError = e.message ?: "Ошибка создания") }
                },
            )
        }
    }

    // ---------- Пригласить сотрудника (2D) ----------

    fun openStaffInviteDialog() = _state.update {
        it.copy(showStaffInviteDialog = true, staffInviteError = null,
            staffInviteRole = "instructor", staffInvitePermissions = emptySet(),
            createdStaffInviteCode = null)
    }
    fun dismissStaffInviteDialog() = _state.update {
        it.copy(showStaffInviteDialog = false, staffInviteError = null,
            createdStaffInviteCode = null)
    }
    fun selectStaffInviteRole(role: String) = _state.update {
        it.copy(staffInviteRole = role, staffInvitePermissions = emptySet())
    }
    fun toggleStaffPermission(perm: String) = _state.update {
        val cur = it.staffInvitePermissions
        val next = if (perm in cur) cur - perm else cur + perm
        it.copy(staffInvitePermissions = next)
    }

    fun createStaffInvite() {
        val me = _state.value.currentUser ?: return
        val clubId = me.clubId ?: return
        val role = _state.value.staffInviteRole
        val perms = if (role == "director") _state.value.staffInvitePermissions.toList() else emptyList()

        viewModelScope.launch {
            val result = createInvite(role = role, clubId = clubId, permissions = perms)
            result.fold(
                onSuccess = { invite ->
                    _state.update { it.copy(staffInviteError = null, createdStaffInviteCode = invite.code) }
                },
                onFailure = { e ->
                    _state.update { it.copy(staffInviteError = e.message ?: "Ошибка создания") }
                },
            )
        }
    }

    // ---------- Передача по email (2E) ----------

    fun openTransferByEmailDialog() = _state.update {
        it.copy(showTransferByEmailDialog = true, transferEmail = "",
            transferByEmailError = null, transferByEmailSuccess = false)
    }
    fun dismissTransferByEmailDialog() = _state.update {
        it.copy(showTransferByEmailDialog = false, transferEmail = "",
            transferByEmailError = null, transferByEmailSuccess = false)
    }
    fun onTransferEmailChange(v: String) = _state.update { it.copy(transferEmail = v) }

    fun transferByEmail(onSuccess: () -> Unit) {
        val me = _state.value.currentUser ?: return
        val clubId = me.clubId ?: return
        val email = _state.value.transferEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            _state.update { it.copy(transferByEmailError = "Введите корректный email") }
            return
        }
        viewModelScope.launch {
            val result = transferByEmail(
                oldOwnerId = me.id,
                email = email,
                clubId = clubId,
                userRole = me.role,
            )
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            showTransferByEmailDialog = false,
                            transferByEmailError = null,
                            transferByEmailSuccess = true,
                        )
                    }
                    onSuccess()
                },
                onFailure = { e ->
                    _state.update { it.copy(transferByEmailError = e.message ?: "Ошибка передачи") }
                },
            )
        }
    }

    fun confirmTransfer(onSuccess: () -> Unit) {
        val me = _state.value.currentUser ?: return
        val target = _state.value.transferTarget ?: return
        val clubId = me.clubId ?: return

        viewModelScope.launch {
            val result = transferClubOwnership(
                oldOwnerId = me.id,
                newOwnerId = target.id,
                clubId = clubId,
                clubName = "",
                userRole = me.role,
            )
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        showTransferDialog = false,
                        showConfirmDialog = false,
                        transferTarget = null,
                        transferError = null,
                    )
                }
                onSuccess()
            } else {
                _state.update {
                    it.copy(
                        showConfirmDialog = false,
                        transferError = result.exceptionOrNull()?.message ?: "Ошибка передачи",
                    )
                }
            }
        }
    }
}
