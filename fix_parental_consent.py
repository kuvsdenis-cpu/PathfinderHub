#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Parental consent (teen side) (08.10.2026)
Запуск:
    python fix_parental_consent.py
"""

import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()


def die(msg):
    print("[ERROR] " + msg)
    sys.exit(1)


def patch(path, replacements, backup=True):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if backup:
        bak = p.with_suffix(p.suffix + ".bak")
        if not bak.exists():
            shutil.copy2(p, bak)
            print("[BAK]  " + bak.name)

    for i, (old, new) in enumerate(replacements, 1):
        if old not in text:
            die(path + ": шаблон #" + str(i) + " не найден.\n---\n"
                + old[:400] + "\n---")
        if text.count(old) > 1:
            print("[WARN] " + path + ": шаблон #" + str(i)
                  + " встречается " + str(text.count(old)) + " раз")
        text = text.replace(old, new, 1)

    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 1. UserRepository.kt - метод для запроса согласия
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/data/repository/UserRepository.kt", [
    ("""    suspend fun updateAccessLevel(id: String, l: String) {""",
     """    /**
     * Teen отправляет запрос родителю на согласие участия в клубе.
     * Обновляет email родителя, статус и время запроса в UserEntity.
     *
     * Если email родителя новый — сохраняем его. Если уже был —
     * просто переводим статус в pending и обновляем дату.
     */
    suspend fun requestParentalConsent(
        childId: String,
        parentEmail: String,
        requestedAt: Long = System.currentTimeMillis(),
    ) {
        val current = dao.getUser(childId) ?: return
        val updated = current.copy(
            parentEmail = parentEmail.trim().lowercase(),
            parentalConsentStatus = "pending",
            parentalConsentRequestedAt = java.util.Date(requestedAt),
        )
        dao.update(updated)
        firestore.updateUser(updated)
        syncQueueManager.enqueue("user", childId, "UPDATE", updated)
        Log.d(TAG, "requestParentalConsent: $childId -> $parentEmail")
    }

    suspend fun updateAccessLevel(id: String, l: String) {"""),
])


# ============================================================
# 2. TeenProfileViewModel.kt - состояние и метод
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileViewModel.kt", [
    ("""data class TeenProfileUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val completedLevels: Int = 0,
    val achievements: Int = 0,
    val loggedOut: Boolean = false,   // <-- ДОБАВЛЕНО
)""",
     """data class TeenProfileUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val completedLevels: Int = 0,
    val achievements: Int = 0,
    val loggedOut: Boolean = false,
    val isSendingConsent: Boolean = false,
    val consentMessage: String? = null,
)"""),

    ("""    /**
     * Выход: Firebase Auth + очистка локальной сессии.
     * После успеха ставим флаг loggedOut — UI отреагирует и навигирует на LOGIN.
     */""",
     """    /**
     * Teen отправляет запрос родителю на согласие участия.
     * После успеха обновляем state — UI покажет статус "pending".
     */
    fun requestParentalConsent(parentEmail: String) {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(consentMessage = "Сессия истекла") }
            return
        }
        if (parentEmail.isBlank() || !parentEmail.contains("@")) {
            _state.update { it.copy(consentMessage = "Укажите корректный email") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSendingConsent = true, consentMessage = null) }
            try {
                userRepository.requestParentalConsent(userId, parentEmail)
                _state.update {
                    it.copy(
                        isSendingConsent = false,
                        consentMessage = "Запрос отправлен на $parentEmail",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSendingConsent = false,
                        consentMessage = "Ошибка: ${e.message ?: "не удалось отправить"}",
                    )
                }
            }
        }
    }

    fun clearConsentMessage() {
        _state.update { it.copy(consentMessage = null) }
    }

    /**
     * Выход: Firebase Auth + очистка локальной сессии.
     * После успеха ставим флаг loggedOut — UI отреагирует и навигирует на LOGIN.
     */"""),
])


# ============================================================
# 3. TeenProfileScreen.kt - секция согласия родителя
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileScreen.kt", [
    # 1) state: диалог email
    ("""    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }""",
     """    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showParentEmailDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }"""),

    # 2) Scaffold: snackbar host
    ("""    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },""",
     """    LaunchedEffect(state.consentMessage) {
        state.consentMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearConsentMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },"""),

    # 3) Секция согласия после StatCard-ов
    ("""            StatCard("Пройдено ступеней", state.completedLevels.toString())
            Spacer(Modifier.height(8.dp))
            StatCard("Достижений", state.achievements.toString())

            Spacer(Modifier.height(32.dp))""",
     """            StatCard("Пройдено ступеней", state.completedLevels.toString())
            Spacer(Modifier.height(8.dp))
            StatCard("Достижений", state.achievements.toString())

            Spacer(Modifier.height(24.dp))

            // ==================== СОГЛАСИЕ РОДИТЕЛЯ ====================
            ParentalConsentSection(
                status = u.parentalConsentStatus,
                parentEmail = u.parentEmail,
                isSending = state.isSendingConsent,
                onRequest = { showParentEmailDialog = true }
            )

            Spacer(Modifier.height(32.dp))"""),

    # 4) Диалог email родителя
    ("""    // Диалог подтверждения выхода
    if (showLogoutDialog) {""",
     """    if (showParentEmailDialog) {
        ParentEmailDialog(
            initialEmail = state.user?.parentEmail ?: "",
            onDismiss = { showParentEmailDialog = false },
            onConfirm = { email ->
                viewModel.requestParentalConsent(email)
                showParentEmailDialog = false
            }
        )
    }

    // Диалог подтверждения выхода
    if (showLogoutDialog) {"""),

    # 5) Новые composable-функции в конец файла
    ("""@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = PathfinderBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}""",
     """@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = PathfinderBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ParentalConsentSection(
    status: String,
    parentEmail: String?,
    isSending: Boolean,
    onRequest: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Согласие родителя",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            when (status) {
                "approved" -> {
                    Text(
                        "Согласие получено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PathfinderBlue
                    )
                    if (!parentEmail.isNullOrBlank()) {
                        Text(
                            "Родитель: $parentEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                "pending" -> {
                    Text(
                        "Запрос отправлен",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!parentEmail.isNullOrBlank()) {
                        Text(
                            "Ожидаем подтверждения от $parentEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRequest, enabled = !isSending) {
                        Text("Изменить email родителя")
                    }
                }
                "rejected" -> {
                    Text(
                        "Родитель отклонил согласие",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequest, enabled = !isSending) {
                        Text("Отправить повторно")
                    }
                }
                else -> {
                    Text(
                        "Отправьте запрос — родитель подтвердит участие в клубе.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRequest,
                        enabled = !isSending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Отправить запрос родителю")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentEmailDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Email родителя") },
        text = {
            Column {
                Text("На этот адрес придёт запрос на согласие участия в клубе.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(email) },
                enabled = email.contains("@") && email.length >= 5
            ) { Text("Отправить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}"""),
])

print()
print("Готово.")
print("Теперь в TeenProfile -> секция 'Согласие родителя'.")
print("Если статус 'not_requested' - кнопка 'Отправить запрос родителю'.")
print("После отправки статус становится 'pending', поле parentalConsentRequestedAt")
print("заполняется - родитель сможет подтвердить при регистрации (позже).")