package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.ParentChildLinkRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParentWithChildren(
    val parent: UserEntity,
    val children: List<UserEntity>,
)

data class ManageParentsUiState(
    val isLoading: Boolean = true,
    val parentsWithChildren: List<ParentWithChildren> = emptyList(),
    val allChildren: List<UserEntity> = emptyList(),
    val errorMessage: String? = null,
    val savedMessage: String? = null,

    val showLinkDialog: Boolean = false,
    val linkTarget: ParentWithChildren? = null,
    val linkSelectedChildId: String? = null,

    val showRevokeDialog: Boolean = false,
    val revokeTarget: ParentChildLinkEntity? = null,
    val revokeTargetName: String = "",
    val revokeReason: String = "",
)

@HiltViewModel
class ManageParentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentChildLinkRepo: ParentChildLinkRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageParentsUiState())
    val state: StateFlow<ManageParentsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val meId = sessionManager.getUserId() ?: run {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(meId)
            if (me == null || !me.isOwnerClub) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Только владелец клуба управляет привязкой"
                    )
                }
                return@launch
            }
            val clubId = me.clubId ?: run {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }

            viewModelScope.launch {
                userRepository.observeClubMembersByRoles(clubId, listOf("teen")).collect { teens ->
                    _state.update { it.copy(allChildren = teens) }
                }
            }

            viewModelScope.launch {
                combine(
                    userRepository.observeClubMembersByRoles(clubId, listOf("parent")),
                    parentChildLinkRepo.observeLinksByClub(clubId),
                ) { parents, links ->
                    val byParent = links.groupBy { it.parentId }
                    parents.map { p ->
                        val childIds = byParent[p.id].orEmpty()
                            .filter { it.revokedAt == null }
                            .map { it.childId }
                        val children = childIds.mapNotNull { cid ->
                            _state.value.allChildren.find { it.id == cid }
                        }
                        ParentWithChildren(parent = p, children = children)
                    }
                }.collect { result ->
                    _state.update { it.copy(isLoading = false, parentsWithChildren = result) }
                }
            }
        }
    }

    fun openLinkDialog(pwc: ParentWithChildren) {
        _state.update {
            it.copy(showLinkDialog = true, linkTarget = pwc, linkSelectedChildId = null)
        }
    }

    fun dismissLinkDialog() {
        _state.update { it.copy(showLinkDialog = false, linkTarget = null, linkSelectedChildId = null) }
    }

    fun selectChildForLink(childId: String?) {
        _state.update { it.copy(linkSelectedChildId = childId) }
    }

    fun confirmLink() {
        val target = _state.value.linkTarget ?: return
        val childId = _state.value.linkSelectedChildId ?: return
        val clubId = target.parent.clubId ?: return
        _state.update { it.copy(showLinkDialog = false) }
        viewModelScope.launch {
            try {
                val existing = parentChildLinkRepo.getByParentAndChild(target.parent.id, childId)
                if (existing != null && existing.revokedAt == null) {
                    _state.update { it.copy(errorMessage = "Уже привязан") }
                    return@launch
                }
                parentChildLinkRepo.link(
                    parentId = target.parent.id,
                    childId = childId,
                    clubId = clubId,
                    linkedBy = sessionManager.getUserId() ?: "",
                )
                _state.update { it.copy(savedMessage = "Ребёнок привязан") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка привязки") }
            }
        }
    }

    /**
     * Открыть диалог «Отвязать» по (parentId, childId) — вызывается из ParentCard,
     * где у нас нет готового ParentChildLinkEntity, только ребёнок.
     */
    fun openRevokeDialogByChild(parentId: String, childId: String, name: String) {
        viewModelScope.launch {
            val link = parentChildLinkRepo.getByParentAndChild(parentId, childId)
            if (link == null || link.revokedAt != null) return@launch
            _state.update {
                it.copy(
                    showRevokeDialog = true,
                    revokeTarget = link,
                    revokeTargetName = name,
                    revokeReason = "",
                )
            }
        }
    }

    fun dismissRevokeDialog() {
        _state.update { it.copy(showRevokeDialog = false, revokeTarget = null, revokeReason = "") }
    }

    fun onRevokeReasonChange(v: String) {
        _state.update { it.copy(revokeReason = v) }
    }

    fun confirmRevoke() {
        val link = _state.value.revokeTarget ?: return
        val reason = _state.value.revokeReason.takeIf { it.isNotBlank() }
        _state.update { it.copy(showRevokeDialog = false) }
        viewModelScope.launch {
            try {
                parentChildLinkRepo.revoke(link.parentId, link.childId, reason)
                _state.update { it.copy(savedMessage = "Связь отозвана") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message ?: "Ошибка отвязки") }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(savedMessage = null, errorMessage = null) }
    }
}