#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
K4 part 3 (invite UI) - 09.10.2026

- DirectorInvitesViewModel: +clubs, +selectedClubId, +permissions, +чекбоксы
- DirectorInvitesScreen: чип "Со-директор" + выбор клуба + группы прав
- CreateInviteUseCase: +clubId, +permissions
- InviteRepository.createInvite: +permissions
- RegisterByInviteUseCase: при регистрации — clubIds, isOwnerClub, directorPermissions
- FirestoreInviteDataSource: попытка добавить permissions в map (если найдём)

Запуск:
    python fix_k4_part3.py
"""

import re, shutil, sys
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


def patch(path, repls, skip_if_marker=None, optional=False):
    p = ROOT / path
    if not p.exists():
        if optional:
            print("[SKIP] " + path + " (не найден, optional)")
            return
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    for i, (o, n) in enumerate(repls, 1):
        if o not in text:
            if optional:
                print("[WARN] " + path + ": патч #" + str(i) + " не найден, пропускаю")
                continue
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. InviteRepository.createInvite — +permissions
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/InviteRepository.kt", [
    ("""    suspend fun createInvite(
        clubId: String,
        role: String,
        createdBy: String,
        lifetimeDays: Int = 7
    ): InviteEntity {""",
     """    suspend fun createInvite(
        clubId: String,
        role: String,
        createdBy: String,
        lifetimeDays: Int = 7,
        permissions: List<String> = emptyList(),
    ): InviteEntity {"""),
    ("""        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            role = role,
            createdBy = createdBy,""",
     """        val invite = InviteEntity(
            id = UUID.randomUUID().toString(),
            code = generateCode(),
            clubId = clubId,
            role = role,
            permissions = permissions,
            createdBy = createdBy,"""),
], skip_if_marker="permissions: List<String> = emptyList()")


# ============================================================
# 2. CreateInviteUseCase — +clubId, +permissions
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/usecase/director/CreateInviteUseCase.kt", [
    ("""    /**
     * Создать инвайт-код от имени текущего директора.
     *
     * @param role — какую роль получит приглашённый:
     *               teen | parent | instructor | secretary
     */
    suspend operator fun invoke(role: String): Result<InviteEntity> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("Нет активной сессии"))

            val user = userRepository.getUser(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            if (user.role != "director") {
                return Result.failure(Exception("Только директор может создавать инвайты"))
            }

            val clubId = user.clubId
                ?: return Result.failure(Exception("У пользователя нет клуба"))

            val invite = inviteRepository.createInvite(
                clubId = clubId,
                role = role,
                createdBy = userId
            )
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }""",
     """    /**
     * Создать инвайт-код от имени текущего директора.
     *
     * @param role — какую роль получит приглашённый:
     *               teen | parent | instructor | secretary | director
     * @param clubId — в какой клуб приглашаем. null = активный клуб.
     * @param permissions — права для director (co-director).
     */
    suspend operator fun invoke(
        role: String,
        clubId: String? = null,
        permissions: List<String> = emptyList(),
    ): Result<InviteEntity> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("Нет активной сессии"))

            val user = userRepository.getUser(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            if (user.role != "director") {
                return Result.failure(Exception("Только директор может создавать инвайты"))
            }

            val targetClubId = clubId ?: user.clubId
                ?: return Result.failure(Exception("Не указан клуб"))

            // Проверяем, что этот клуб действительно у директора
            val available = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
            if (targetClubId !in available) {
                return Result.failure(Exception("Нет доступа к этому клубу"))
            }

            val invite = inviteRepository.createInvite(
                clubId = targetClubId,
                role = role,
                createdBy = userId,
                permissions = if (role == "director") permissions else emptyList(),
            )
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }"""),
], skip_if_marker="targetClubId")


# ============================================================
# 3. RegisterByInviteUseCase — заполнить clubIds, isOwnerClub, permissions
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/usecase/auth/RegisterByInviteUseCase.kt", [
    ("""            role = invite.role,
            clubId = invite.clubId,
            conferenceId = null,""",
     """            role = invite.role,
            clubId = invite.clubId,
            clubIds = listOf(invite.clubId),
            isOwnerClub = invite.role != "director",
            directorPermissions = if (invite.role == "director") invite.permissions else emptyList(),
            conferenceId = null,"""),
], skip_if_marker="directorPermissions = if (invite.role")


# ============================================================
# 4. FirestoreInviteDataSource — попытаться добавить permissions в map
# ============================================================
fids = ROOT / "app/src/main/java/com/pathfinder/hub/data/remote/FirestoreInviteDataSource.kt"
if fids.exists():
    raw = fids.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    if "permissions" in text:
        print("[SKIP] FirestoreInviteDataSource уже содержит permissions")
    else:
        # Ищем "role" to invite.role и добавляем строку после
        m = re.search(r'("role"\s+to\s+invite\.role\s*,)', text)
        if m:
            bak = fids.with_suffix(fids.suffix + ".bak." + STAMP)
            shutil.copy2(fids, bak)
            print("[BAK]  " + bak.name)
            insertion = m.group(1) + '\n            "permissions" to invite.permissions,'
            text = text[:m.start(1)] + insertion + text[m.end(1):]
            fids.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
            print("[OK]   FirestoreInviteDataSource.kt")
        else:
            print("[WARN] FirestoreInviteDataSource: не нашёл '\"role\" to invite.role'.")
            print("       Пришли файл — добавлю permissions вручную.")
else:
    print("[WARN] FirestoreInviteDataSource.kt не найден")


# ============================================================
# 5. DirectorInvitesViewModel.kt — REWRITE
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.home.director

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.data.repository.ClubRepository
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.permissions.PermissionRegistry
import com.pathfinder.hub.domain.usecase.director.CreateInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectorInvitesUiState(
    val invites: List<InviteEntity> = emptyList(),
    val clubs: List<ClubEntity> = emptyList(),
    val selectedClubId: String? = null,
    val isLoading: Boolean = false,
    val lastCreated: InviteEntity? = null,
    val errorMessage: String? = null,
    val selectedRole: String = "teen",
    val selectedPermissions: Set<String> = emptySet(),
)

@HiltViewModel
class DirectorInvitesViewModel @Inject constructor(
    private val createInviteUseCase: CreateInviteUseCase,
    private val inviteRepository: InviteRepository,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorInvitesUiState())
    val state: StateFlow<DirectorInvitesUiState> = _state.asStateFlow()

    init {
        observeDirector()
        observeInvites()
    }

    private fun observeDirector() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user == null) return@collect
                val ids = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
                val clubs = ids.mapNotNull { id ->
                    try { clubRepository.getClub(id) } catch (_: Exception) { null }
                }
                val activeId = user.clubId ?: clubs.firstOrNull()?.id
                _state.update {
                    it.copy(
                        clubs = clubs,
                        selectedClubId = it.selectedClubId ?: activeId,
                    )
                }
            }
        }
    }

    private fun observeInvites() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val user = userRepository.getUser(userId) ?: return@launch
            val clubId = user.clubId ?: return@launch
            inviteRepository.observeByClub(clubId).collect { list ->
                _state.update { it.copy(invites = list) }
            }
        }
    }

    fun onRoleSelected(role: String) {
        _state.update {
            it.copy(
                selectedRole = role,
                // При выборе "director" — предзаполняем безопасным набором
                selectedPermissions = if (role == "director")
                    PermissionRegistry.DEFAULT_CO_DIRECTOR.toSet()
                else emptySet(),
            )
        }
    }

    fun onClubSelected(clubId: String) {
        _state.update { it.copy(selectedClubId = clubId) }
    }

    fun onPermissionToggle(perm: String) {
        _state.update {
            val cur = it.selectedPermissions
            val next = if (perm in cur) cur - perm else cur + perm
            it.copy(selectedPermissions = next)
        }
    }

    fun createInvite() {
        val s = _state.value
        val role = s.selectedRole
        val clubId = s.selectedClubId

        if (role == "director" && clubId == null) {
            _state.update { it.copy(errorMessage = "Не выбран клуб") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null, lastCreated = null) }

        viewModelScope.launch {
            val permissions = if (role == "director") s.selectedPermissions.toList() else emptyList()
            createInviteUseCase(role = role, clubId = clubId, permissions = permissions).fold(
                onSuccess = { invite ->
                    _state.update { it.copy(isLoading = false, lastCreated = invite) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Ошибка создания инвайта"
                        )
                    }
                }
            )
        }
    }

    fun clearLastCreated() {
        _state.update { it.copy(lastCreated = null) }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorInvitesViewModel.kt", VM)


# ============================================================
# 6. DirectorInvitesScreen.kt — REWRITE
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.home.director

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.domain.permissions.PermissionRegistry
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorInvitesScreen(
    onBack: () -> Unit,
    viewModel: DirectorInvitesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инвайты") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Кого приглашаем?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "teen" to "Следопыт",
                    "parent" to "Родитель",
                    "instructor" to "Наставник",
                    "director" to "Со-директор",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.selectedRole == value,
                        onClick = { viewModel.onRoleSelected(value) },
                        label = { Text(label) }
                    )
                }
            }

            // ---------- Выбор клуба (только для director и если >1) ----------
            if (state.selectedRole == "director" && state.clubs.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Клуб",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.clubs.forEach { club ->
                        FilterChip(
                            selected = state.selectedClubId == club.id,
                            onClick = { viewModel.onClubSelected(club.id) },
                            label = { Text(club.name) }
                        )
                    }
                }
            }

            // ---------- Чекбоксы прав (только для director) ----------
            if (state.selectedRole == "director") {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Права со-директора",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Не отмечено — со-директор без доступа к этим функциям",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                PermissionRegistry.GROUPS.forEach { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            group.permissions.forEach { perm ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = perm.key in state.selectedPermissions,
                                        onCheckedChange = { viewModel.onPermissionToggle(perm.key) }
                                    )
                                    Text(perm.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::createInvite,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать инвайт-код")
                }
            }

            state.lastCreated?.let { invite ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Новый код создан",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            invite.code,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val fmt = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                        Text("Действует до: ${fmt.format(invite.expiresAt)}")
                        if (invite.role == "director") {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Права: ${invite.permissions.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, buildShareMessage(invite.code, invite.role))
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Поделиться")
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Все инвайты клуба",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            state.invites.forEach { invite ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                invite.code,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Роль: ${roleLabel(invite.role)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    when (invite.status) {
                                        "active" -> "Активен"
                                        "used" -> "Использован"
                                        "expired" -> "Истёк"
                                        "revoked" -> "Отозван"
                                        else -> invite.status
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "teen" -> "Следопыт"
    "parent" -> "Родитель"
    "instructor" -> "Наставник"
    "director" -> "Со-директор"
    "secretary" -> "Секретарь"
    else -> role
}

private fun buildShareMessage(code: String, role: String): String = when (role) {
    "director" -> """
        Приглашение со-директора в клуб Pathfinder Hub.

        Инвайт-код: $code

        Скачайте приложение и введите код при регистрации.
        Код действует 7 дней.
    """.trimIndent()
    else -> """
        Приглашение в клуб «Следопыт»!

        Ваш инвайт-код: $code

        Скачайте приложение Pathfinder Hub и введите этот код при регистрации.
        Код действует 7 дней.
    """.trimIndent()
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorInvitesScreen.kt", SCREEN)


print()
print("=" * 60)
print("K4 part 3 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - InviteRepository.createInvite: +permissions")
print("  - CreateInviteUseCase: +clubId, +permissions, валидация клуба")
print("  - RegisterByInviteUseCase: clubIds=[invite.clubId],")
print("      isOwnerClub = (role != 'director'),")
print("      directorPermissions = invite.permissions")
print("  - DirectorInvitesScreen: чип 'Со-директор' + чипы клубов +")
print("      чекбоксы прав (8 групп)")
print()
print("Проверка:")
print("  1. Директор → Инвайты → чип 'Со-директор'")
print("  2. Если клубов >1 → чипы клубов")
print("  3. Чекбоксы прав по группам (по умолчанию предзаполнены безопасным набором)")
print("  4. 'Создать инвайт-код' → появляется код")
print("  5. Другой человек регистрируется по коду → становится со-директором")
print("  6. Со-директор входит → у него вкладки клубов как у директора")
print()
print("ВАЖНО: если у тебя FirestoreInviteDataSource сохраняет поля через mapOf явно,")
print("скрипт попытается добавить permissions. Если не найдёт — увидишь [WARN], пришли файл.")
print()
print("Откат: .bak.<timestamp>")