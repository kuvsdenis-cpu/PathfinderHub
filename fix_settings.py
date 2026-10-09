#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Settings (theme + notifications) (08.10.2026)
Запуск:
    python fix_settings.py
"""

import shutil
import sys
from pathlib import Path

ROOT = Path.cwd()


def die(msg):
    print("[ERROR] " + msg)
    sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


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
# 1. SettingsRepository.kt
# ============================================================
REPO = '''package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.NotificationDao
import com.pathfinder.hub.data.local.dao.SettingsDao
import com.pathfinder.hub.data.local.entity.notifications.NotificationPreferencesEntity
import com.pathfinder.hub.data.local.entity.settings.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val notificationDao: NotificationDao,
) {

    // ==================== Тема ====================

    fun observeUserSettings(userId: String): Flow<UserSettingsEntity?> =
        settingsDao.observeUserSettings(userId)

    suspend fun setTheme(userId: String, theme: String) {
        val current = settingsDao.observeUserSettings(userId).first()
        val updated = (current
            ?: UserSettingsEntity(userId = userId, theme = theme, updatedAt = Date()))
            .copy(theme = theme, updatedAt = Date())
        settingsDao.upsertUserSettings(updated)
    }

    // ==================== Уведомления ====================

    fun observeNotificationPrefs(userId: String): Flow<NotificationPreferencesEntity?> =
        notificationDao.observePrefs(userId)

    private suspend fun defaultPrefs(userId: String): NotificationPreferencesEntity {
        notificationDao.observePrefs(userId).first()?.let { return it }
        return NotificationPreferencesEntity(
            userId = userId,
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

    suspend fun setPushEnabled(userId: String, enabled: Boolean) {
        val p = defaultPrefs(userId).copy(pushEnabled = enabled)
        notificationDao.upsertPrefs(p)
    }

    suspend fun setEmailEnabled(userId: String, enabled: Boolean) {
        val p = defaultPrefs(userId).copy(emailEnabled = enabled)
        notificationDao.upsertPrefs(p)
    }

    suspend fun setCategory(userId: String, category: String, enabled: Boolean) {
        val cur = defaultPrefs(userId)
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

    suspend fun setQuietHours(userId: String, enabled: Boolean) {
        val p = defaultPrefs(userId).copy(quietHoursEnabled = enabled)
        notificationDao.upsertPrefs(p)
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/data/repository/SettingsRepository.kt", REPO)


# ============================================================
# 2. SettingsViewModel.kt
# ============================================================
VM = '''package com.pathfinder.hub.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.notifications.NotificationPreferencesEntity
import com.pathfinder.hub.data.local.entity.settings.UserSettingsEntity
import com.pathfinder.hub.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
                settingsRepository.observeUserSettings(uid).collect { s: UserSettingsEntity? ->
                    _state.update { it.copy(theme = s?.theme ?: "system") }
                }
            }
            viewModelScope.launch {
                settingsRepository.observeNotificationPrefs(uid).collect { p ->
                    _state.update { it.copy(prefs = p, isLoading = false) }
                }
            }
        }
    }

    fun onThemeChange(theme: String) {
        val uid = userId ?: return
        viewModelScope.launch { settingsRepository.setTheme(uid, theme) }
    }

    fun onPushToggle(enabled: Boolean) {
        val uid = userId ?: return
        viewModelScope.launch { settingsRepository.setPushEnabled(uid, enabled) }
    }

    fun onEmailToggle(enabled: Boolean) {
        val uid = userId ?: return
        viewModelScope.launch { settingsRepository.setEmailEnabled(uid, enabled) }
    }

    fun onCategoryToggle(category: String, enabled: Boolean) {
        val uid = userId ?: return
        viewModelScope.launch { settingsRepository.setCategory(uid, category, enabled) }
    }

    fun onQuietHoursToggle(enabled: Boolean) {
        val uid = userId ?: return
        viewModelScope.launch { settingsRepository.setQuietHours(uid, enabled) }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/settings/SettingsViewModel.kt", VM)


# ============================================================
# 3. SettingsScreen.kt
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
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
            // ============ Тема ============
            SettingsCard(title = "Тема оформления") {
                val options = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.theme == value,
                            onClick = { viewModel.onThemeChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            // ============ Уведомления ============
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
                    subtitle = "Не беспокоить ночью (22:00–08:00)",
                    checked = state.prefs?.quietHoursEnabled ?: false,
                    onToggle = viewModel::onQuietHoursToggle,
                )
            }

            // ============ Категории ============
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
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
        Switch(
            checked = value ?: true,
            onCheckedChange = { vm.onCategoryToggle(key, it) }
        )
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/settings/SettingsScreen.kt", SCREEN)


# ============================================================
# 4. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    // === Teen - Profile ===
    const val TEEN_PROFILE = "teen_profile\"""",
     """    // === Teen - Profile ===
    const val TEEN_PROFILE = "teen_profile"
    const val SETTINGS = "settings\""""),
])


# ============================================================
# 5. PathfinderNavHost.kt - новый маршрут
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    # import
    ("import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen",
     "import com.pathfinder.hub.ui.screens.profile.TeenProfileScreen\n"
     "import com.pathfinder.hub.ui.screens.settings.SettingsScreen"),

    # маршрут TEEN_PROFILE -> добавить onNavigateToSettings
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
])


# ============================================================
# 6. TeenProfileScreen.kt - кнопка "Настройки" + nav
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileScreen.kt", [
    # import Settings icon
    ("import androidx.compose.material.icons.automirrored.filled.Logout",
     "import androidx.compose.material.icons.automirrored.filled.Logout\n"
     "import androidx.compose.material.icons.filled.Settings"),

    # сигнатура + новая lambda
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

    # topBar actions - кнопка Настройки
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
])


# ============================================================
# 7. MainActivity.kt - theme override
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/MainActivity.kt", [
    # import
    ("import com.pathfinder.hub.data.repository.UserRepository",
     "import com.pathfinder.hub.data.repository.SettingsRepository\n"
     "import com.pathfinder.hub.data.repository.UserRepository"),

    ("import androidx.compose.ui.Modifier",
     "import androidx.compose.runtime.mutableStateOf\n"
     "import androidx.compose.foundation.isSystemInDarkTheme\n"
     "import androidx.compose.ui.Modifier"),

    # инжект
    ("""    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var chatNotifier: ChatNotificationHelper""",
     """    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var chatNotifier: ChatNotificationHelper
    @Inject lateinit var settingsRepository: SettingsRepository

    private val themePreference = mutableStateOf("system")"""),

    # собираем тему в onCreate
    ("""        handleDeepLink(intent)
        askNotificationPermission()

        lifecycleScope.launch {
            val startDestination = resolveStartDestination()""",
     """        handleDeepLink(intent)
        askNotificationPermission()

        // Тема: подписка на настройки пользователя
        lifecycleScope.launch {
            val uid = sessionManager.getUserId()
            if (uid != null) {
                settingsRepository.observeUserSettings(uid).collect { s ->
                    themePreference.value = s?.theme ?: "system"
                }
            }
        }

        lifecycleScope.launch {
            val startDestination = resolveStartDestination()"""),

    # setContent: применить тему
    ("""            setContent {
                PathfinderTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        PathfinderNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }""",
     """            setContent {
                val dark = when (themePreference.value) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
                PathfinderTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        PathfinderNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }"""),
])


print()
print("Готово.")
print("---")
print("Проверка:")
print("  1. Пересобери APK")
print("  2. Открой Профиль -> иконка шестерёнки в правом верхнем углу")
print("  3. Выбери 'Тёмная' - приложение сразу переключится")
print("  4. Выключи 'Push-уведомления' - состояние сохранится после перезапуска")