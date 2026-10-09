#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Parent N2 (payments + messages) - 09.10.2026
Запуск:
    python fix_parent_n2.py
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
# 1. ParentPaymentsViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.PaymentEntity
import com.pathfinder.hub.data.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParentPaymentsUiState(
    val isLoading: Boolean = true,
    val payments: List<PaymentEntity> = emptyList(),
    val processingId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ParentPaymentsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentPaymentsUiState())
    val state: StateFlow<ParentPaymentsUiState> = _state.asStateFlow()

    private val parentId: String? = sessionManager.getUserId()

    init {
        val uid = parentId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                parentRepository.observeParentPayments(uid).collect { list ->
                    _state.update { it.copy(isLoading = false, payments = list) }
                }
            }
        }
    }

    /**
     * Родитель отмечает оплату. Статус меняется на "paid".
     * Директор потом подтверждает → "confirmed".
     */
    fun markPaid(payment: PaymentEntity) {
        val uid = parentId ?: return
        _state.update { it.copy(processingId = payment.id, errorMessage = null) }
        viewModelScope.launch {
            try {
                parentRepository.upsertPayment(
                    payment.copy(
                        status = "paid",
                        paidAt = java.util.Date(),
                        paymentMethod = "manual",
                    )
                )
                _state.update {
                    it.copy(processingId = null, infoMessage = "Отмечено как оплачено")
                }
            } catch (e: Exception) {
                _state.update { it.copy(processingId = null, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentPaymentsViewModel.kt", VM)


# ============================================================
# 2. ParentPaymentsScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.home.parent

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
fun ParentPaymentsScreen(
    onBack: () -> Unit,
    viewModel: ParentPaymentsViewModel = hiltViewModel(),
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
                title = { Text("Взносы") },
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
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.payments.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AccountBalance, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Взносов пока нет", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.payments, key = { it.id }) { p ->
                    PaymentCard(
                        payment = p,
                        fmt = fmt,
                        isProcessing = state.processingId == p.id,
                        onPay = { viewModel.markPaid(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentEntity,
    fmt: SimpleDateFormat,
    isProcessing: Boolean,
    onPay: () -> Unit,
) {
    val statusLabel = when (payment.status) {
        "pending" -> "К оплате"
        "paid" -> "Оплачено, ждём подтверждения"
        "confirmed" -> "Оплата подтверждена"
        "overdue" -> "Просрочено"
        else -> payment.status
    }
    val statusColor = when (payment.status) {
        "confirmed" -> PathfinderGreen
        "paid" -> PathfinderBlue
        "overdue" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountBalance, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        payment.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Срок: " + fmt.format(payment.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "%.0f %s".format(payment.amount, payment.currency),
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
                        if (payment.status == "confirmed") Icons.Default.CheckCircle
                        else Icons.Default.Schedule,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            if (payment.status == "pending" || payment.status == "overdue") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPay,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Отметить как оплачено")
                    }
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentPaymentsScreen.kt", SCREEN)


# ============================================================
# 3. ParentMessagesViewModel.kt
# ============================================================
MSG_VM = '''package com.pathfinder.hub.ui.screens.home.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.data.repository.ParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ParentMessagesUiState(
    val isLoading: Boolean = true,
    val messages: List<ParentMessageEntity> = emptyList(),
    val replyTarget: ParentMessageEntity? = null,
    val replyText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class ParentMessagesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val parentRepository: ParentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentMessagesUiState())
    val state: StateFlow<ParentMessagesUiState> = _state.asStateFlow()

    private val parentId: String? = sessionManager.getUserId()

    init {
        val uid = parentId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                parentRepository.observeMessages(uid).collect { list ->
                    _state.update { it.copy(isLoading = false, messages = list) }
                }
            }
        }
    }

    fun openReply(m: ParentMessageEntity) {
        _state.update { it.copy(replyTarget = m, replyText = m.response ?: "") }
    }

    fun closeReply() {
        _state.update { it.copy(replyTarget = null, replyText = "") }
    }

    fun onReplyChange(text: String) {
        _state.update { it.copy(replyText = text) }
    }

    fun sendReply() {
        val m = _state.value.replyTarget ?: return
        val text = _state.value.replyText.trim()
        if (text.isBlank()) return

        _state.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                parentRepository.upsertMessage(
                    m.copy(
                        response = text,
                        respondedAt = Date(),
                        pendingSync = true,
                    )
                )
                _state.update {
                    it.copy(
                        isSending = false,
                        replyTarget = null,
                        replyText = "",
                        infoMessage = "Ответ отправлен",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentMessagesViewModel.kt", MSG_VM)


# ============================================================
# 4. ParentMessagesScreen.kt
# ============================================================
MSG_SCREEN = '''package com.pathfinder.hub.ui.screens.home.parent

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pathfinder.hub.data.local.entity.parent.ParentMessageEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMessagesScreen(
    onBack: () -> Unit,
    viewModel: ParentMessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))

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
                title = { Text("Сообщения от клуба") },
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
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.messages.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Email, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Сообщений пока нет", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { m ->
                    MessageCard(m, fmt, onReply = { viewModel.openReply(m) })
                }
            }
        }
    }

    state.replyTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { viewModel.closeReply() },
            title = { Text("Ответ") },
            text = {
                Column {
                    Text(
                        m.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        m.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.replyText,
                        onValueChange = viewModel::onReplyChange,
                        label = { Text("Ваш ответ") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendReply() },
                    enabled = !state.isSending && state.replyText.isNotBlank(),
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Отправить")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeReply() }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun MessageCard(
    m: ParentMessageEntity,
    fmt: SimpleDateFormat,
    onReply: () -> Unit,
) {
    val hasReply = !m.response.isNullOrBlank()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasReply)
                PathfinderGreen.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Email, null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        fmt.format(m.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(m.message, style = MaterialTheme.typography.bodyMedium)

            if (hasReply) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = PathfinderGreen.copy(alpha = 0.15f)
                    )
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Ваш ответ",
                            style = MaterialTheme.typography.labelSmall,
                            color = PathfinderGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(m.response ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReply) { Text("Изменить ответ") }
            } else {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onReply,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Ответить")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/home/parent/ParentMessagesScreen.kt", MSG_SCREEN)


# ============================================================
# 5. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val CHILD_PROGRESS = "child_progress/{childId}"
    fun childProgress(childId: String) = "child_progress/$childId\"""",
     """    const val CHILD_PROGRESS = "child_progress/{childId}"
    fun childProgress(childId: String) = "child_progress/$childId"
    const val PARENT_PAYMENTS = "parent_payments"
    const val PARENT_MESSAGES = "parent_messages\""""),
], skip_if_marker='const val PARENT_PAYMENTS')


# ============================================================
# 6. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.home.parent.ChildProgressScreen",
     "import com.pathfinder.hub.ui.screens.home.parent.ChildProgressScreen\n"
     "import com.pathfinder.hub.ui.screens.home.parent.ParentPaymentsScreen\n"
     "import com.pathfinder.hub.ui.screens.home.parent.ParentMessagesScreen"),

    # ParentHomeScreen — подключить колбэки
    ("""                onOpenChild = { childId ->
                    navController.navigate(Routes.childProgress(childId))
                },
                onLogout = {""",
     """                onOpenChild = { childId ->
                    navController.navigate(Routes.childProgress(childId))
                },
                onNavigateToPayments = { navController.navigate(Routes.PARENT_PAYMENTS) },
                onNavigateToMessages = { navController.navigate(Routes.PARENT_MESSAGES) },
                onLogout = {"""),

    # composable после CHILD_PROGRESS
    ("""        composable(
            route = Routes.CHILD_PROGRESS,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            ChildProgressScreen(onBack = { navController.popBackStack() })
        }""",
     """        composable(
            route = Routes.CHILD_PROGRESS,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            ChildProgressScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PARENT_PAYMENTS) {
            ParentPaymentsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PARENT_MESSAGES) {
            ParentMessagesScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.PARENT_PAYMENTS")


print()
print("=" * 60)
print("N2 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - ParentPaymentsScreen + VM — список взносов, кнопка 'Оплатить'")
print("  - ParentMessagesScreen + VM — сообщения от клуба + ответ")
print("  - Routes.PARENT_PAYMENTS / PARENT_MESSAGES")
print("  - NavHost — подключены колбэки")
print()
print("Проверка:")
print("  1. Вход под parent → 'Оплата' / 'Сообщения'")
print("  2. Оплата: список (пусто, пока директор не создаст платёж)")
print("  3. Сообщения: список (пусто, пока директор не отправит)")
print()
print("Создание платежей и сообщений — директорская сторона.")
print("Это N2.5 — отдельный скрипт.")
print()
print("Откат: .bak.<timestamp>")