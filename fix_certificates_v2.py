#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fix certificates (H) v2:
  - MyCertificatesViewModel: без return в init
  - PathfinderNavHost: правильные анкоры (импорт + TEEN_HOME)
  - TeenHomeScreen: параметр + ряд Чаты+Сертификаты

Запуск:
    python fix_certificates_v2.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def read_text(path):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    return p, text, crlf


def write_text(p, text, crlf):
    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))


def bak(p):
    b = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, b)
    print("[BAK]  " + b.name)


# ============================================================
# 1. MyCertificatesViewModel.kt — переписать (убрать return в init)
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class CertificateItem(
    val certificateId: String,
    val title: String,
    val name: String,
    val issuedAt: Date,
    val kind: String,
    val targetId: String,
)

data class MyCertificatesUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val items: List<CertificateItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class MyCertificatesViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyCertificatesUiState())
    val state: StateFlow<MyCertificatesUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Пользователь не авторизован")
            }
        } else {
            load(uid)
        }
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(userId)
                val name = listOfNotNull(user?.firstName, user?.lastName)
                    .joinToString(" ").trim().ifBlank { "Следопыт" }
                _state.update { it.copy(userName = name) }

                levelRepository.observeCompletions(userId).collect { levels ->
                    val items = levels.map { lc ->
                        val lvl = levelRepository.getLevel(lc.levelId)
                        CertificateItem(
                            certificateId = "level_" + lc.levelId,
                            title = "Сертификат о прохождении ступени",
                            name = lvl?.name ?: lc.levelId,
                            issuedAt = lc.completedAt,
                            kind = "level",
                            targetId = lc.levelId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "level" } + items)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }

        viewModelScope.launch {
            try {
                honorRepository.observeCompletions(userId).collect { honors ->
                    val items = honors.map { hc ->
                        val h = honorRepository.getHonor(hc.honorId)
                        CertificateItem(
                            certificateId = "honor_" + hc.honorId,
                            title = "Сертификат о специализации",
                            name = h?.name ?: hc.honorId,
                            issuedAt = hc.completedAt,
                            kind = "honor",
                            targetId = hc.honorId,
                        )
                    }
                    _state.update { s ->
                        s.copy(
                            items = (s.items.filter { it.kind != "honor" } + items)
                                .sortedByDescending { it.issuedAt },
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }
}
'''

p, _, _ = read_text("app/src/main/java/com/pathfinder/hub/ui/screens/certificates/MyCertificatesViewModel.kt")
bak(p)
p.write_text(VM, encoding="utf-8", newline="\n")
print("[REWRITE] MyCertificatesViewModel.kt")


# ============================================================
# 2. PathfinderNavHost.kt
# ============================================================
p, text, crlf = read_text("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt")

# 2a. Импорт — анкор: реальный импорт ChallengeDetailScreen (без .screens.)
anchor_import = "import com.pathfinder.hub.ui.challenges.ChallengeDetailScreen\n"
new_import = anchor_import + "import com.pathfinder.hub.ui.screens.certificates.MyCertificatesScreen\n"

if "MyCertificatesScreen" not in text:
    if anchor_import not in text:
        die("NavHost: не нашёл анкор импорта ChallengeDetailScreen")
    text = text.replace(anchor_import, new_import, 1)
    print("[OK]   NavHost: импорт MyCertificatesScreen")
else:
    print("[SKIP] NavHost: импорт уже есть")

# 2b. TEEN_HOME — параметр + composable
old_teen = (
    "                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },\n"
    "                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }\n"
    "            )\n"
    "        }\n"
    "\n"
    "        // === Teen - Levels ==="
)
new_teen = (
    "                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },\n"
    "                onNavigateToCertificates = { navController.navigate(Routes.MY_CERTIFICATES) },\n"
    "                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }\n"
    "            )\n"
    "        }\n"
    "\n"
    "        composable(Routes.MY_CERTIFICATES) {\n"
    "            MyCertificatesScreen(onBack = { navController.popBackStack() })\n"
    "        }\n"
    "\n"
    "        // === Teen - Levels ==="
)

if "Routes.MY_CERTIFICATES" not in text:
    if old_teen not in text:
        die("NavHost: не нашёл блок TEEN_HOME → '// === Teen - Levels ==='")
    text = text.replace(old_teen, new_teen, 1)
    print("[OK]   NavHost: composable MY_CERTIFICATES + параметр")
else:
    print("[SKIP] NavHost: MY_CERTIFICATES уже есть")

bak(p)
write_text(p, text, crlf)


# ============================================================
# 3. TeenHomeScreen.kt
# ============================================================
p, text, crlf = read_text("app/src/main/java/com/pathfinder/hub/ui/screens/home/teen/TeenHomeScreen.kt")

if "onNavigateToCertificates" in text:
    print("[SKIP] TeenHomeScreen: уже пропатчен")
else:
    # 3a. import Description
    imp_old = (
        "import androidx.compose.material.icons.filled.CheckCircle\n"
        "import androidx.compose.material.icons.filled.EmojiEvents"
    )
    imp_new = (
        "import androidx.compose.material.icons.filled.CheckCircle\n"
        "import androidx.compose.material.icons.filled.Description\n"
        "import androidx.compose.material.icons.filled.EmojiEvents"
    )
    if imp_old not in text:
        die("TeenHome: не нашёл import CheckCircle/EmojiEvents")
    text = text.replace(imp_old, imp_new, 1)

    # 3b. сигнатура
    sig_old = (
        "    onNavigateToChats: () -> Unit = {},\n"
        "    onNavigateToNotifications: () -> Unit = {},\n"
        "    viewModel: TeenHomeViewModel = hiltViewModel()"
    )
    sig_new = (
        "    onNavigateToChats: () -> Unit = {},\n"
        "    onNavigateToCertificates: () -> Unit = {},\n"
        "    onNavigateToNotifications: () -> Unit = {},\n"
        "    viewModel: TeenHomeViewModel = hiltViewModel()"
    )
    if sig_old not in text:
        die("TeenHome: не нашёл сигнатуру с onNavigateToChats")
    text = text.replace(sig_old, sig_new, 1)

    # 3c. Ряд 4
    row_old = (
        "            // Кнопки разделов - Ряд 4 (Чат клуба)\n"
        "            Row(\n"
        "                modifier = Modifier.fillMaxWidth(),\n"
        "                horizontalArrangement = Arrangement.spacedBy(8.dp)\n"
        "            ) {\n"
        "                ActionTile(\n"
        "                    icon = Icons.Default.Chat,\n"
        '                    label = "Чаты",\n'
        "                    onClick = onNavigateToChats,\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "                Spacer(modifier = Modifier.weight(1f))\n"
        "            }"
    )
    row_new = (
        "            // Кнопки разделов - Ряд 4 (Чаты + Сертификаты)\n"
        "            Row(\n"
        "                modifier = Modifier.fillMaxWidth(),\n"
        "                horizontalArrangement = Arrangement.spacedBy(8.dp)\n"
        "            ) {\n"
        "                ActionTile(\n"
        "                    icon = Icons.Default.Chat,\n"
        '                    label = "Чаты",\n'
        "                    onClick = onNavigateToChats,\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "                ActionTile(\n"
        "                    icon = Icons.Default.Description,\n"
        '                    label = "Сертификаты",\n'
        "                    onClick = onNavigateToCertificates,\n"
        "                    modifier = Modifier.weight(1f)\n"
        "                )\n"
        "            }"
    )
    if row_old not in text:
        die("TeenHome: не нашёл 'Ряд 4 (Чат клуба)'")
    text = text.replace(row_old, row_new, 1)

    bak(p)
    write_text(p, text, crlf)
    print("[OK]   TeenHomeScreen обновлён")


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)