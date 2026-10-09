#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Settings v2 (theme + notifications) - 08.10.2026

ВАЖНО:
  - НЕ трогает SettingsRepository.kt и RepositoryModule.kt.
  - Theme читает через SettingsRepository (твой, с 1 аргументом dao).
  - Notification preferences пишет напрямую через NotificationDao.
  - MainActivity: объединяет офлайн-автологин + подписку на тему.
  - .bak с таймстампом (защита от потери старого .bak).

Запуск:
    python fix_settings_v2.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(msg):
    print("[ERROR] " + msg)
    sys.exit(1)


def make_bak(p):
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        die(path + ": файл уже существует. Ничего не перезаписываю.")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def patch(path, replacements, skip_if_marker=None):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    raw = p.read_bytes()
    crlf = b"\r\n" in raw
    text = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")

    if skip_if_marker and skip_if_marker in text:
        print("[SKIP] " + path + " (уже пропатчен)")
        return

    make_bak(p)
    for i, (old, new) in enumerate(replacements, 1):
        if old not in text:
            die(path + ": шаблон #" + str(i) + " не найден.\n---\n"
                + old[:400] + "\n---")
        text = text.replace(old, new, 1)

    p.write_bytes((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))
    print("[OK]   " + path)


# ============================================================
# 0. Защита: не запускать на сломанном Routes.kt
# ============================================================
routes_text = (ROOT / "app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt").read_text(encoding="utf-8")
if "customGroupChat" not in routes_text or "CREATE_GROUP_CHAT" not in routes_text:
    die("Routes.kt не содержит константы групповых чатов. "
        "Сначала запусти fix_routes_recover.py.")


# ============================================================
# 1. SettingsViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.dao.NotificationDao
import com.pathfinder.hub.data.local.entity.notifications.NotificationPreferencesEntity
import com.pathfinder.hub.data.local.entity.settings.UserSettingsEntity
import com.pathfinder.hub.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val theme: String = "system",
    val prefs: NotificationPreferencesEntity? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationDao: NotificationDao,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val userId: String? = sessionManager.getUserId()

    init {
        val uid = userId
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Пользователь не авторизован") }
        } else {
            viewModelScope.launch {
                settingsRepository.observeUserSettings(uid).collect { s ->
                    _state.update { it.copy(theme = s?.theme ?: "system") }
                }
            }
            viewModelScope.launch {
                notificationDao.observePrefs(uid).collect { p ->
                    _state.update { it.copy(prefs = p, isLoading = false) }
                }
            }
        }
    }

    fun onThemeChange(theme: String) {
        val uid = userId ?: return
        viewModelScope.launch {
            val current = settingsRepository.observeUserSettings(uid).first()
            val base = current
                ?: UserSettingsEntity(userId = uid, theme = theme, updatedAt = Date())
            settingsRepository.upsertUserSettings(
                base.copy(theme = theme, updatedAt = Date())
            )
        }
    }

    private suspend fun currentPrefs(): NotificationPreferencesEntity {
        val uid = userId ?: error("no user")
        return notificationDao.observePrefs(uid).first()
            ?: NotificationPreferencesEntity(
                userId = uid,
                quietHoursEnabled = false,
                quietHoursStart = 22,
                quietHoursEnd = 8,
                dailyPushLimit = 100,
                pushEnabled = true,
                emailEnabled = false,
                digestEnabled = false,
                digestDay = "sunday",
                digestTime = "09:00",
                categoryTasks = true,
                categoryModeration = true,
                categoryLearning = true,
                categoryGamification = true,
                categoryEvents = true,
                categoryConsent = true,
                categoryProgress = true,
                categoryPayments = true,
                categoryContact = true,
            )
    }

    fun onPushToggle(enabled: Boolean) {
        viewModelScope.launch {
            notificationDao.upsertPrefs(currentPrefs().copy(pushEnabled = enabled))
        }
    }

    fun onEmailToggle(enabled: Boolean) {
        viewModelScope.launch {
            notificationDao.upsertPrefs(currentPrefs().copy(emailEnabled = enabled))
        }
    }

    fun onQuietHoursToggle(enabled: Boolean) {
        viewModelScope.launch {
            notificationDao.upsertPrefs(currentPrefs().copy(quietHoursEnabled = enabled))
        }
    }

    fun onCategoryToggle(category: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = currentPrefs()
            val p = when (category) {
                "tasks" -> cur.copy(categoryTasks = enabled)
                "moderation" -> cur.copy(categoryModeration = enabled)
                "learning" -> cur.copy(categoryLearning = enabled)
                "gamification" -> cur.copy(categoryGamification = enabled)
                "events" -> cur.copy(categoryEvents = enabled)
                "consent" -> cur.copy(categoryConsent = enabled)
                "progress" -> cur.copy(categoryProgress = enabled)
                "payments" -> cur.copy(categoryPayments = enabled)
                "contact" -> cur.copy(categoryContact = enabled)
                else -> cur
            }
            notificationDao.upsertPrefs(p)
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/settings/SettingsViewModel.kt", VM)


# ============================================================
# 2. SettingsScreen.kt
# ============================================================
SCREEN = '''package com.pathfinder.hub.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(title = "Тема оформления") {
                val options = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.theme == value,
                            onClick = { viewModel.onThemeChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label) }
                    }
                }
            }

            SettingsCard(title = "Уведомления") {
                ToggleRow(
                    title = "Push-уведомления",
                    subtitle = "Мгновенные уведомления на устройство",
                    checked = state.prefs?.pushEnabled ?: true,
                    onToggle = viewModel::onPushToggle,
                )
                ToggleRow(
                    title = "Email-уведомления",
                    subtitle = "Дублировать важное на почту",
                    checked = state.prefs?.emailEnabled ?: false,
                    onToggle = viewModel::onEmailToggle,
                )
                ToggleRow(
                    title = "Тихие часы",
                    subtitle = "Не беспокоить ночью (22:00-08:00)",
                    checked = state.prefs?.quietHoursEnabled ?: false,
                    onToggle = viewModel::onQuietHoursToggle,
                )
            }

            SettingsCard(title = "Категории уведомлений") {
                CategoryToggle("Задачи", "tasks", state.prefs?.categoryTasks, viewModel)
                CategoryToggle("Модерация", "moderation", state.prefs?.categoryModeration, viewModel)
                CategoryToggle("Обучение", "learning", state.prefs?.categoryLearning, viewModel)
                CategoryToggle("Геймификация", "gamification", state.prefs?.categoryGamification, viewModel)
                CategoryToggle("События", "events", state.prefs?.categoryEvents, viewModel)
                CategoryToggle("Согласие родителя", "consent", state.prefs?.categoryConsent, viewModel)
                CategoryToggle("Прогресс", "progress", state.prefs?.categoryProgress, viewModel)
                CategoryToggle("Платежи", "payments", state.prefs?.categoryPayments, viewModel)
                CategoryToggle("Контакты", "contact", state.prefs?.categoryContact, viewModel)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun CategoryToggle(
    label: String,
    key: String,
    value: Boolean?,
    vm: SettingsViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value ?: true, onCheckedChange = { vm.onCategoryToggle(key, it) })
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/settings/SettingsScreen.kt", SCREEN)


# ============================================================
# 3. Routes.kt — добавить SETTINGS
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    // === Teen - Profile ===
    const val TEEN_PROFILE = "teen_profile\"""",
     """    // === Teen - Profile ===
    const val TEEN_PROFILE = "teen_profile"
    const val SETTINGS = "settings\""""),
], skip_if_marker='const val SETTINGS = "settings"')


# ============================================================
# 4. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    # import SettingsScreen
    ("import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen",
     "import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen\n"
     "import com.pathfinder.hub.ui.screens.settings.SettingsScreen"),

    # TEEN_PROFILE: добавить onNavigateToSettings + SETTINGS composable
    ("""        composable(Routes.TEEN_PROFILE) {
            TeenProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }""",
     """        composable(Routes.TEEN_PROFILE) {
            TeenProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }"""),
], skip_if_marker="Routes.SETTINGS")


# ============================================================
# 5. TeenProfileScreen.kt — иконка шестерёнки + параметр
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileScreen.kt", [
    # import иконки
    ("import androidx.compose.material.icons.automirrored.filled.Logout",
     "import androidx.compose.material.icons.automirrored.filled.Logout\n"
     "import androidx.compose.material.icons.filled.Settings"),

    # сигнатура функции
    ("""fun TeenProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,                       // <-- ДОБАВЛЕНО
    viewModel: TeenProfileViewModel = hiltViewModel()
) {""",
     """fun TeenProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: TeenProfileViewModel = hiltViewModel()
) {"""),

    # topBar actions
    ("""                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )""",
     """                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )"""),
], skip_if_marker="onNavigateToSettings")


# ============================================================
# 6. MainActivity.kt — офлайн-автологин + тема
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/MainActivity.kt", [
    # import-блок
    ("""import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier""",
     """import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier"""),

    # import SettingsRepository
    ("import com.pathfinder.hub.data.repository.FcmTokenRepository\nimport com.pathfinder.hub.data.repository.UserRepository",
     "import com.pathfinder.hub.data.repository.FcmTokenRepository\nimport com.pathfinder.hub.data.repository.SettingsRepository\nimport com.pathfinder.hub.data.repository.UserRepository"),

    # инжект
    ("""    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var chatNotifier: ChatNotificationHelper""",
     """    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var chatNotifier: ChatNotificationHelper
    @Inject lateinit var settingsRepository: SettingsRepository"""),

    # onCreate — полностью
    ("""        handleDeepLink(intent)
        askNotificationPermission()

        lifecycleScope.launch {
            val startDestination = resolveStartDestination()
            registerFcmTokenIfLoggedIn()
            checkAndNotifyUnreadIfLoggedIn()

            setContent {
                PathfinderTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        PathfinderNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }
    }""",
     """        handleDeepLink(intent)
        askNotificationPermission()

        // UI стартует СРАЗУ (офлайн-автологин). resolveStartDestination() читает Room.
        setContent {
            val uid = sessionManager.getUserId()
            var themePref by remember { mutableStateOf("system") }
            LaunchedEffect(uid) {
                if (uid != null) {
                    settingsRepository.observeUserSettings(uid).collect { s ->
                        themePref = s?.theme ?: "system"
                    }
                }
            }
            val dark = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            PathfinderTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        startDestination = resolveStartDestination()
                    }

                    val dest = startDestination
                    if (dest == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        PathfinderNavHost(
                            navController = navController,
                            startDestination = dest
                        )
                    }
                }
            }
        }

        // Фоновые задачи: FCM, summary. Не блокируют UI.
        lifecycleScope.launch {
            registerFcmTokenIfLoggedIn()
            checkAndNotifyUnreadIfLoggedIn()
        }
    }"""),
], skip_if_marker="settingsRepository.observeUserSettings")


print()
print("=" * 60)
print("Готово. Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)