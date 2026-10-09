#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Parent N1.5 (link parent<->child) - 09.10.2026
Запуск:
    python fix_parent_n15.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


# ============================================================
# 1. ClubMembersViewModel.kt — REWRITE
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.ChatRepository
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // ---------- Диалог привязки parent<->child ----------
    val linkDialogMember: UserEntity? = null,
    val linkDialogCandidates: List<UserEntity> = emptyList(),
    val linkDialogSelected: String? = null,
)

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
        loadMembers()
    }

    private fun loadMembers() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
            return
        }

        viewModelScope.launch {
            try {
                val me = userRepository.getUser(userId)
                if (me == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не найден") }
                    return@launch
                }
                val clubId = me.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        currentUserId = userId,
                        currentUserRole = me.role,
                        clubId = clubId,
                    )
                }

                userRepository.observeClubMembers(clubId).collect { members ->
                    _state.update { it.copy(members = members) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun changeRole(userId: String, newRole: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId) ?: return@launch
                userRepository.update(user.copy(role = newRole))
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

    // ============================================================
    // Привязка parent<->child
    // ============================================================

    /**
     * Открыть диалог привязки. Для parent — показываем teen'ов,
     * для teen — показываем parent'ов.
     */
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
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/ClubMembersViewModel.kt", VM)


# ============================================================
# 2. ClubMembersScreen.kt — REWRITE
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.home.director

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubMembersScreen(
    onBack: () -> Unit,
    onOpenDM: (dmId: String, otherUid: String) -> Unit = { _, _ -> },
    viewModel: ClubMembersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var removeDialogUser by remember { mutableStateOf<UserEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.pendingDmId, state.pendingOtherUid) {
        val dmId = state.pendingDmId
        val other = state.pendingOtherUid
        if (dmId != null && other != null) {
            onOpenDM(dmId, other)
            viewModel.clearPendingDM()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Участники клуба") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            state.members.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("В клубе пока нет участников", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(state.members, key = { it.id }) { member ->
                        MemberCard(
                            member = member,
                            isCurrentUser = member.id == state.currentUserId,
                            currentUserRole = state.currentUserRole,
                            onChangeRole = { newRole -> viewModel.changeRole(member.id, newRole) },
                            onSuspend = { viewModel.suspendUser(member.id) },
                            onRemove = { removeDialogUser = member },
                            onWrite = { viewModel.openDM(member.id) },
                            onLinkParent = { viewModel.openLinkDialog(member) },
                        )
                    }
                }
            }
        }
    }

    // ---------- Диалог удаления ----------
    removeDialogUser?.let { user ->
        AlertDialog(
            onDismissRequest = { removeDialogUser = null },
            title = { Text("Удалить участника") },
            text = { Text("Вы уверены, что хотите удалить ${user.firstName} ${user.lastName} из клуба? Это действие нельзя отменить.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeUser(user.id)
                        removeDialogUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { removeDialogUser = null }) { Text("Отмена") }
            }
        )
    }

    // ---------- Диалог привязки parent<->child ----------
    state.linkDialogMember?.let { member ->
        val isParent = member.role == "parent"
        val dialogTitle = if (isParent) "Привязать к ребёнку" else "Привязать родителя"
        val emptyText = if (isParent)
            "В клубе нет следопытов для привязки"
        else
            "В клубе нет родителей для привязки"

        AlertDialog(
            onDismissRequest = { viewModel.closeLinkDialog() },
            title = { Text(dialogTitle) },
            text = {
                Column {
                    Text(
                        "${member.firstName} ${member.lastName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))

                    if (state.linkDialogCandidates.isEmpty()) {
                        Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column {
                            state.linkDialogCandidates.forEach { candidate ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = state.linkDialogSelected == candidate.id,
                                        onClick = { viewModel.onLinkCandidateSelected(candidate.id) }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${candidate.firstName} ${candidate.lastName}".trim(),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            getRoleName(candidate.role),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmLink() },
                    enabled = state.linkDialogSelected != null,
                ) { Text("Привязать") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeLinkDialog() }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun MemberCard(
    member: UserEntity,
    isCurrentUser: Boolean,
    currentUserRole: String,
    onChangeRole: (String) -> Unit,
    onSuspend: () -> Unit,
    onRemove: () -> Unit,
    onWrite: () -> Unit = {},
    onLinkParent: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ---------- Верхняя строка ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (isCurrentUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = getInitials(member.firstName, member.lastName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentUser)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${member.firstName} ${member.lastName}".trim(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isCurrentUser) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Вы",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = getRoleName(member.role),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AssistChip(
                    onClick = { },
                    label = { Text(getStatusName(member.status)) }
                )
            }

            // ---------- Нижняя строка ----------
            if (!isCurrentUser) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDM(currentUserRole, member.role)) {
                        TextButton(onClick = onWrite) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Написать")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Действия")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (member.role == "parent" || member.role == "teen") {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (member.role == "parent")
                                                "Привязать к ребёнку"
                                            else
                                                "Привязать родителя"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PersonAdd, null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onLinkParent()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Сменить роль") },
                                onClick = {
                                    menuExpanded = false
                                    roleMenuExpanded = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Приостановить доступ") },
                                onClick = {
                                    menuExpanded = false
                                    onSuspend()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить из клуба") },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (roleMenuExpanded) {
        AlertDialog(
            onDismissRequest = { roleMenuExpanded = false },
            title = { Text("Выберите новую роль") },
            text = {
                Column {
                    listOf(
                        "teen" to "Следопыт",
                        "instructor" to "Наставник",
                        "secretary" to "Секретарь"
                    ).forEach { (role, name) ->
                        TextButton(
                            onClick = {
                                onChangeRole(role)
                                roleMenuExpanded = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { roleMenuExpanded = false }) { Text("Отмена") }
            }
        )
    }
}

private fun getInitials(firstName: String, lastName: String): String {
    val f = firstName.firstOrNull()?.uppercase() ?: ""
    val l = lastName.firstOrNull()?.uppercase() ?: ""
    return (f + l).ifBlank { "?" }
}

private fun getRoleName(role: String): String = when (role) {
    "teen" -> "Следопыт"
    "parent" -> "Родитель"
    "instructor" -> "Наставник"
    "director" -> "Директор"
    "secretary" -> "Секретарь"
    "conference_admin" -> "Администратор конференции"
    "conference_secretary" -> "Секретарь конференции"
    else -> role
}

private fun getStatusName(status: String): String = when (status) {
    "active" -> "Активен"
    "pending" -> "Ожидает"
    "suspended" -> "Приостановлен"
    "deleted" -> "Удалён"
    else -> status
}

private fun canDM(current: String, target: String): Boolean {
    if (current == target) return false
    return when (current) {
        "teen" -> target in listOf("director", "instructor")
        "director", "instructor", "secretary", "conference_admin" -> target != "parent"
        else -> false
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/ClubMembersScreen.kt", SCREEN)


print()
print("=" * 60)
print("N1.5 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - ClubMembersViewModel: +ParentRepository")
print("  - +состояние диалога привязки (linkDialogMember/Candidates/Selected)")
print("  - +openLinkDialog / closeLinkDialog / onLinkCandidateSelected / confirmLink")
print("  - ClubMembersScreen: +пункт меню 'Привязать к ребёнку' (parent) /")
print("      'Привязать родителя' (teen)")
print("  - +диалог RadioButton выбора кандидата")
print("  - +snackbar при успехе")
print()
print("Проверка end-to-end:")
print("  1. Директор → Участники")
print("  2. На карточке parent → ⋮ → 'Привязать к ребёнку'")
print("  3. Выбрать teen → 'Привязать'")
print("  4. Войти под parent → 'Мои дети' → ребёнок с прогрессом")
print("  5. Тап → ChildProgressScreen (ступени/специализации/ачивки)")
print()
print("Обратный сценарий:")
print("  - На карточке teen → 'Привязать родителя' → выбрать parent")
print()
print("Откат: .bak.<timestamp>")