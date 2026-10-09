#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Parent N2.5 (director creates payments) - 09.10.2026
Запуск:
    python fix_parent_n25.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, repls, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
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
            die(path + ": шаблон #" + str(i) + ":\n" + o[:400])
        text = text.replace(o, n, 1)
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. PermissionRegistry — добавить группу "Финансы"
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/domain/permissions/PermissionRegistry.kt", [
    ("""        Group("Настройки клуба", listOf(
            Permission("settings.view", "Просмотр настроек"),
            Permission("settings.edit", "Изменение настроек"),
        )),""",
     """        Group("Настройки клуба", listOf(
            Permission("settings.view", "Просмотр настроек"),
            Permission("settings.edit", "Изменение настроек"),
        )),
        Group("Финансы", listOf(
            Permission("finance.view", "Просмотр платежей"),
            Permission("finance.create", "Создание платежей"),
            Permission("finance.confirm", "Подтверждение оплат"),
        )),"""),
], skip_if_marker='"finance.create"')


# ============================================================
# 2. DirectorPaymentsViewModel.kt
# ============================================================
DP_VM = '''package com.pathfinder.hub.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectorPaymentsUiState(
    val isLoading: Boolean = true,
    val all: List<PaymentEntity> = [],
    val filtered: List<PaymentEntity> = [],
    val selectedFilter: String = "waiting",  // "all" | "waiting" | "paid" | "confirmed"
    val namesByParent: Map<String, String> = emptyMap(),
    val processingId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class DirectorPaymentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorPaymentsUiState())
    val state: StateFlow<DirectorPaymentsUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            // Собираем все платежи клуба через всех родителей.
            // ParentDao не имеет observeAllByClub — используем members.
            userRepository.observeClubMembersByRole(clubId, "parent").collect { parents ->
                // подписки на платежи каждого parent
                // Простой подход: сложим всё что подтянется в один список
                val names = parents.associate { it.id to "${it.firstName} ${it.lastName}".trim() }
                _state.update { it.copy(namesByParent = names, isLoading = false) }

                // Перезагружаем подписки
                parents.forEach { p ->
                    launch {
                        parentRepository.observeParentPayments(p.id).collect { list ->
                            // Обновляем агрегат: удаляем старые записи этого parent, добавляем новые
                            _state.update { s ->
                                val others = s.all.filter { it.parentId != p.id }
                                val merged = (others + list).sortedByDescending { it.dueDate }
                                s.copy(all = merged, filtered = applyFilter(merged, s.selectedFilter))
                            }
                        }
                    }
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _state.update {
            it.copy(selectedFilter = filter, filtered = applyFilter(it.all, filter))
        }
    }

    private fun applyFilter(list: List<PaymentEntity>, filter: String): List<PaymentEntity> {
        return when (filter) {
            "waiting" -> list.filter { it.status == "pending" || it.status == "paid" || it.status == "overdue" }
            "paid" -> list.filter { it.status == "paid" }
            "confirmed" -> list.filter { it.status == "confirmed" }
            else -> list
        }
    }

    fun confirm(p: PaymentEntity) {
        val uid = sessionManager.getUserId() ?: return
        _state.update { it.copy(processingId = p.id) }
        viewModelScope.launch {
            try {
                parentRepository.confirmPayment(p.id, "confirmed", uid, System.currentTimeMillis())
                _state.update {
                    it.copy(processingId = null, infoMessage = "Оплата подтверждена")
                }
            } catch (e: Exception) {
                _state.update { it.copy(processingId = null, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/payments/DirectorPaymentsViewModel.kt", DP_VM)


# ============================================================
# 3. DirectorPaymentsScreen.kt
# ============================================================
DP_SCREEN = '''package com.pathfinder.hub.ui.screens.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorPaymentsScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    viewModel: DirectorPaymentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Взносы клуба") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Default.AccountBalance, "Создать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Фильтры
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "waiting" to "Активные",
                    "paid" to "Ждут подтверждения",
                    "confirmed" to "Оплачено",
                    "all" to "Все",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.selectedFilter == key,
                        onClick = { viewModel.setFilter(key) },
                        label = { Text(label) }
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccountBalance, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Платежей нет", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Нажмите иконку вверху чтобы создать",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.filtered, key = { it.id }) { p ->
                        PaymentRow(
                            p = p,
                            parentName = state.namesByParent[p.parentId] ?: p.parentId,
                            fmt = fmt,
                            isProcessing = state.processingId == p.id,
                            onConfirm = { viewModel.confirm(p) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(
    p: PaymentEntity,
    parentName: String,
    fmt: SimpleDateFormat,
    isProcessing: Boolean,
    onConfirm: () -> Unit,
) {
    val statusLabel = when (p.status) {
        "pending" -> "Ожидает оплаты"
        "paid" -> "Оплачено, ждёт подтверждения"
        "confirmed" -> "Подтверждено"
        "overdue" -> "Просрочено"
        else -> p.status
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        parentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Срок: " + fmt.format(p.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "%.0f %s".format(p.amount, p.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PathfinderBlue
                )
            }

            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = { Text(statusLabel) },
                leadingIcon = {
                    Icon(
                        if (p.status == "confirmed") Icons.Default.CheckCircle
                        else Icons.Default.Schedule,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            if (p.status == "paid") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onConfirm,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Подтвердить оплату")
                    }
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/payments/DirectorPaymentsScreen.kt", DP_SCREEN)


# ============================================================
# 4. CreatePaymentViewModel.kt
# ============================================================
CP_VM = '''package com.pathfinder.hub.ui.screens.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class TeenOption(
    val teen: UserEntity,
    val parents: List<UserEntity>,
)

data class CreatePaymentUiState(
    val isLoading: Boolean = true,
    val clubId: String? = null,
    val teens: List<TeenOption> = emptyList(),
    val selectedTeenId: String? = null,
    val selectedParentId: String? = null,

    val title: String = "",
    val amount: String = "",
    val currency: String = "RUB",
    val dueDate: Date? = null,
    val type: String = "membership",  // membership | event | other
    val immediatelyPaid: Boolean = false,

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CreatePaymentViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatePaymentUiState())
    val state: StateFlow<CreatePaymentUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
            return
        }
        viewModelScope.launch {
            val me = userRepository.getUser(uid)
            val clubId = me?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            _state.update { it.copy(clubId = clubId) }

            userRepository.observeClubMembersByRole(clubId, "teen").collect { teens ->
                val options = mutableListOf<TeenOption>()
                for (t in teens) {
                    val links = parentRepository.observeParents(t.id)
                    // Снимок — первое значение
                    val parents = try {
                        kotlinx.coroutines.flow.first(links)
                    } catch (_: Exception) { emptyList() }
                    val parentUsers = parents
                        .filter { it.status == "active" }
                        .mapNotNull { userRepository.getUser(it.parentId) }
                    options.add(TeenOption(teen = t, parents = parentUsers))
                }
                _state.update { it.copy(isLoading = false, teens = options) }
            }
        }
    }

    fun onTeenSelected(teenId: String) {
        val opt = _state.value.teens.find { it.teen.id == teenId }
        val autoParent = opt?.parents?.firstOrNull()?.id
        _state.update {
            it.copy(
                selectedTeenId = teenId,
                selectedParentId = if (opt?.parents?.size == 1) autoParent else it.selectedParentId,
            )
        }
    }

    fun onParentSelected(parentId: String) = _state.update { it.copy(selectedParentId = parentId) }
    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onAmount(v: String) = _state.update { it.copy(amount = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
    fun onCurrency(v: String) = _state.update { it.copy(currency = v) }
    fun onDueDate(v: Date) = _state.update { it.copy(dueDate = v) }
    fun onType(v: String) = _state.update { it.copy(type = v) }
    fun onImmediatelyPaid(v: Boolean) = _state.update { it.copy(immediatelyPaid = v) }

    fun save() {
        val s = _state.value
        val uid = sessionManager.getUserId() ?: return
        val clubId = s.clubId ?: return

        if (s.selectedTeenId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Выберите ребёнка") }
            return
        }
        if (s.selectedParentId.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Выберите родителя") }
            return
        }
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите назначение") }
            return
        }
        val amount = s.amount.replace(',', '.').toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _state.update { it.copy(errorMessage = "Введите корректную сумму") }
            return
        }
        if (s.dueDate == null) {
            _state.update { it.copy(errorMessage = "Укажите срок") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val now = Date()
                val status = if (s.immediatelyPaid) "confirmed" else "pending"
                val entity = PaymentEntity(
                    id = UUID.randomUUID().toString(),
                    clubId = clubId,
                    childId = s.selectedTeenId,
                    parentId = s.selectedParentId,
                    type = s.type,
                    title = s.title.trim(),
                    amount = amount,
                    currency = s.currency.trim().ifBlank { "RUB" },
                    dueDate = s.dueDate,
                    status = status,
                    paymentMethod = if (s.immediatelyPaid) "manual" else null,
                    paidAt = if (s.immediatelyPaid) now else null,
                    confirmedBy = if (s.immediatelyPaid) uid else null,
                    confirmedAt = if (s.immediatelyPaid) now else null,
                    receiptUrl = null,
                    createdBy = uid,
                    createdAt = now,
                )
                parentRepository.upsertPayment(entity)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/payments/CreatePaymentViewModel.kt", CP_VM)


# ============================================================
# 5. CreatePaymentScreen.kt
# ============================================================
CP_SCREEN = '''package com.pathfinder.hub.ui.screens.payments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePaymentScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreatePaymentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый взнос") },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---------- Ребёнок ----------
            var teenExpanded by remember { mutableStateOf(false) }
            val teenText = state.teens.find { it.teen.id == state.selectedTeenId }
                ?.teen?.let { "${it.firstName} ${it.lastName}".trim() }
                ?: "Выберите ребёнка"

            ExposedDropdownMenuBox(
                expanded = teenExpanded,
                onExpandedChange = { teenExpanded = !teenExpanded }
            ) {
                OutlinedTextField(
                    value = teenText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ребёнок") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving
                )
                ExposedDropdownMenu(
                    expanded = teenExpanded,
                    onDismissRequest = { teenExpanded = false }
                ) {
                    state.teens.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${opt.teen.firstName} ${opt.teen.lastName}".trim() +
                                            if (opt.parents.isEmpty()) " (нет родителя)" else ""
                                )
                            },
                            onClick = {
                                viewModel.onTeenSelected(opt.teen.id)
                                teenExpanded = false
                            }
                        )
                    }
                }
            }

            // ---------- Родитель (если у teen >1) ----------
            val selectedOpt = state.teens.find { it.teen.id == state.selectedTeenId }
            if (selectedOpt != null && selectedOpt.parents.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text("Родитель", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row {
                    selectedOpt.parents.forEach { parent ->
                        FilterChip(
                            selected = state.selectedParentId == parent.id,
                            onClick = { viewModel.onParentSelected(parent.id) },
                            label = { Text("${parent.firstName} ${parent.lastName}".trim()) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Назначение (например, 'Взнос за октябрь')") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )

            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmount,
                    label = { Text("Сумма") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    enabled = !state.isSaving
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = viewModel::onCurrency,
                    label = { Text("Валюта") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isSaving
                )
            }

            Spacer(Modifier.height(12.dp))
            DateField(
                value = state.dueDate,
                label = "Срок оплаты",
                onValueChange = viewModel::onDueDate,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Тип", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row {
                listOf(
                    "membership" to "Взнос",
                    "event" to "Мероприятие",
                    "other" to "Другое",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.type == key,
                        onClick = { viewModel.onType(key) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.immediatelyPaid,
                    onCheckedChange = viewModel::onImmediatelyPaid,
                    enabled = !state.isSaving
                )
                Text("Уже оплачено (закрыть сразу)")
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Создать взнос")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/payments/CreatePaymentScreen.kt", CP_SCREEN)


# ============================================================
# 6. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val PARENT_PAYMENTS = "parent_payments"
    const val PARENT_MESSAGES = "parent_messages\"""",
     """    const val PARENT_PAYMENTS = "parent_payments"
    const val PARENT_MESSAGES = "parent_messages"
    const val DIRECTOR_PAYMENTS = "director_payments"
    const val CREATE_PAYMENT = "create_payment\""""),
], skip_if_marker='const val DIRECTOR_PAYMENTS')


# ============================================================
# 7. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.home.parent.ParentPaymentsScreen",
     "import com.pathfinder.hub.ui.screens.home.parent.ParentPaymentsScreen\n"
     "import com.pathfinder.hub.ui.screens.payments.DirectorPaymentsScreen\n"
     "import com.pathfinder.hub.ui.screens.payments.CreatePaymentScreen"),

    # колбэк в DirectorHome
    ("""                onNavigateToStaff = { navController.navigate(Routes.STAFF) },
                onNavigateToQrQuests = { navController.navigate(Routes.QUEST_MANAGE) },""",
     """                onNavigateToStaff = { navController.navigate(Routes.STAFF) },
                onNavigateToQrQuests = { navController.navigate(Routes.QUEST_MANAGE) },
                onNavigateToPayments = { navController.navigate(Routes.DIRECTOR_PAYMENTS) },"""),

    # composable после REVIEW_HOMEWORK
    ("""        composable(Routes.REVIEW_HOMEWORK) {
            ReviewHomeworkScreen(onBack = { navController.popBackStack() })
        }""",
     """        composable(Routes.REVIEW_HOMEWORK) {
            ReviewHomeworkScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DIRECTOR_PAYMENTS) {
            DirectorPaymentsScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(Routes.CREATE_PAYMENT) }
            )
        }

        composable(Routes.CREATE_PAYMENT) {
            CreatePaymentScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }"""),
], skip_if_marker="Routes.DIRECTOR_PAYMENTS")


# ============================================================
# 8. DirectorHomeScreen.kt — карточка "Взносы"
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", [
    ("""    onNavigateToQrQuests: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},""",
     """    onNavigateToQrQuests: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {},"""),

    ("""                // ---------- Ряд 8: QR-квесты + пусто ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Route,
                        title = "QR-квесты",
                        description = "Серии станций",
                        perm = "qr.create",
                        user = u,
                        onClick = { guard("qr.create") { onNavigateToQrQuests() } },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.weight(1f))
                }""",
     """                // ---------- Ряд 8: QR-квесты + Взносы ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Route,
                        title = "QR-квесты",
                        description = "Серии станций",
                        perm = "qr.create",
                        user = u,
                        onClick = { guard("qr.create") { onNavigateToQrQuests() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.AccountBalance,
                        title = "Взносы",
                        description = "Оплата от родителей",
                        perm = "finance.view",
                        user = u,
                        onClick = { guard("finance.view") { onNavigateToPayments() } },
                        modifier = Modifier.weight(1f),
                    )
                }"""),
], skip_if_marker="onNavigateToPayments")


# ============================================================
# 9. firestore.rules — payments subcollection
# ============================================================
patch("firestore.rules", [
    ("""      match /reports/{reportId} {""",
     """      match /payments/{paymentId} {
        allow read: if isSignedIn() && (
          resource.data.parentId == uid()
          || (isClubStaff() && isOwnClub(clubId))
          || isConferenceAdmin()
        );
        allow create: if isSignedIn() && (hasPerm("finance.create") && isOwnClub(clubId));
        allow update: if isSignedIn() && (
          (resource.data.parentId == uid()
            && request.resource.data.diff(resource.data).affectedKeys()
                .hasOnly(['status', 'paidAt', 'paymentMethod']))
          || (hasPerm("finance.confirm") && isOwnClub(clubId))
          || isConferenceAdmin()
        );
        allow delete: if isConferenceAdmin();
      }

      match /reports/{reportId} {"""),
], skip_if_marker="match /payments/")


print()
print("=" * 60)
print("N2.5 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("  firebase deploy --only firestore:rules")
print()
print("Что сделано:")
print("  - PermissionRegistry: +группа Финансы (view/create/confirm)")
print("  - DirectorPaymentsScreen + VM — список взносов клуба с фильтрами")
print("  - CreatePaymentScreen + VM — создание взноса")
print("  - DirectorHomeScreen: +карточка 'Взносы' (8-й ряд)")
print("  - Routes + NavHost: DIRECTOR_PAYMENTS / CREATE_PAYMENT")
print("  - firestore.rules: payments subcollection")
print()
print("Проверка:")
print("  1. Директор → Главная → 'Взносы'")
print("  2. Иконка вверху → 'Новый взнос'")
print("  3. Выбрать ребёнка (у которого есть родитель)")
print("  4. Назначение, сумма, срок")
print("  5. 'Создать взнос' → платёж в pending")
print("  6. Войти под parent → 'Оплата' → видит платёж → 'Отметить как оплачено'")
print("  7. Войти под директора → 'Взносы' → фильтр 'Ждут подтверждения' → 'Подтвердить'")
print("  8. У родителя статус → 'confirmed'")
print()
print("Автозакрытие: галочка 'Уже оплачено' при создании — сразу confirmed.")
print()
print("Откат: .bak.<timestamp>")