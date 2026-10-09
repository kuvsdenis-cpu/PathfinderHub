#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub - Homework (E) - 08.10.2026

- Teen: вкладка "Домашка" в "Мои задачи"
- Директор/наставник: карточка "Домашка" на главной → создание для всех teen клуба
- Использует существующий HomeworkEntity, AchievementRepository.upsertHomework

Запуск:
    python fix_homework.py
"""

import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m):
    print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        die(path + ": файл уже существует. Ничего не перезаписываю.")
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
# 1. HomeworkTab.kt — ViewModel + UI для teen
# ============================================================
HOMEWORK_TAB = '''package com.pathfinder.hub.ui.screens.homework

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.ui.theme.PathfinderBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class HomeworkUiState(
    val isLoading: Boolean = true,
    val items: List<HomeworkEntity> = emptyList(),
)

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val repo: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeworkUiState())
    val state: StateFlow<HomeworkUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                repo.observeHomework(uid).collect { list ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = list.sortedByDescending { h -> h.dueDate }
                        )
                    }
                }
            }
        }
    }

    fun markDone(id: String) {
        val item = _state.value.items.find { it.id == id } ?: return
        viewModelScope.launch {
            repo.upsertHomework(item.copy(status = "done", pendingSync = true))
        }
    }
}

@Composable
fun HomeworkTabContent(viewModel: HomeworkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }
        state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Домашки пока нет")
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.id }) { h ->
                    HomeworkCard(h, onDone = { viewModel.markDone(h.id) })
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(h: HomeworkEntity, onDone: () -> Unit) {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                h.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (h.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(h.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Срок: ${fmt.format(h.dueDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!h.reward.isNullOrBlank()) {
                Text(
                    "Приз: ${h.reward}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PathfinderBlue
                )
            }
            Spacer(Modifier.height(10.dp))
            when (h.status) {
                "assigned" -> {
                    Button(onClick = onDone) { Text("Отметить выполненным") }
                }
                "done" -> {
                    Text(
                        "Отправлено на проверку",
                        color = PathfinderBlue,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                "approved" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = PathfinderBlue, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Проверено",
                            color = PathfinderBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {
                    Text(
                        "Статус: ${h.status}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/homework/HomeworkTab.kt", HOMEWORK_TAB)


# ============================================================
# 2. CreateHomeworkViewModel.kt
# ============================================================
CREATE_HW_VM = '''package com.pathfinder.hub.ui.screens.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CreateHomeworkUiState(
    val title: String = "",
    val description: String = "",
    val dueDate: String = "",
    val reward: String = "",
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
    val teenCount: Int = 0,
)

@HiltViewModel
class CreateHomeworkViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateHomeworkUiState())
    val state: StateFlow<CreateHomeworkUiState> = _state.asStateFlow()

    private val creatorId: String? = sessionManager.getUserId()

    init {
        val uid = creatorId
        if (uid != null) {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId ?: return@launch
                try {
                    val teens = userRepository.observeClubMembersByRole(clubId, "teen").first()
                    _state.update { it.copy(teenCount = teens.size) }
                } catch (_: Exception) {}
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onDueDateChange(v: String) = _state.update { it.copy(dueDate = v) }
    fun onRewardChange(v: String) = _state.update { it.copy(reward = v) }

    fun create() {
        val s = _state.value
        val uid = creatorId
        if (uid == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.dueDate.isBlank()) {
            _state.update { it.copy(errorMessage = "Укажите срок (yyyy-MM-dd)") }
            return
        }

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed: Date? = try { fmt.parse(s.dueDate) } catch (_: Exception) { null }
        if (parsed == null) {
            _state.update { it.copy(errorMessage = "Неверный формат даты") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                val teens = userRepository.observeClubMembersByRole(clubId, "teen").first()
                if (teens.isEmpty()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "В клубе нет следопытов") }
                    return@launch
                }
                teens.forEach { teen ->
                    achievementRepository.upsertHomework(
                        HomeworkEntity(
                            id = UUID.randomUUID().toString(),
                            clubId = clubId,
                            childId = teen.id,
                            instructorId = uid,
                            title = s.title.trim(),
                            description = s.description.trim(),
                            dueDate = parsed,
                            reward = s.reward.trim().ifBlank { null },
                            status = "assigned",
                            createdAt = Date(),
                            approvedAt = null,
                            pendingSync = true,
                        )
                    )
                }
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/homework/CreateHomeworkViewModel.kt", CREATE_HW_VM)


# ============================================================
# 3. CreateHomeworkScreen.kt
# ============================================================
CREATE_HW_SCREEN = '''package com.pathfinder.hub.ui.screens.homework

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHomeworkScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateHomeworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая домашка") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.teenCount > 0) {
                Text(
                    "Будет назначено ${state.teenCount} следопытам",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PathfinderBlue
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.dueDate,
                onValueChange = viewModel::onDueDateChange,
                label = { Text("Срок (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.reward,
                onValueChange = viewModel::onRewardChange,
                label = { Text("Приз (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::create,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Назначить всем следопытам")
                }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/screens/homework/CreateHomeworkScreen.kt", CREATE_HW_SCREEN)


# ============================================================
# 4. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val CREATE_TASK = "create_task\"""",
     """    const val CREATE_TASK = "create_task"
    const val CREATE_HOMEWORK = "create_homework\""""),
], skip_if_marker='const val CREATE_HOMEWORK')


# ============================================================
# 5. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    # import
    ("import com.pathfinder.hub.ui.screens.events.CreateEventScreen",
     "import com.pathfinder.hub.ui.screens.events.CreateEventScreen\n"
     "import com.pathfinder.hub.ui.screens.homework.CreateHomeworkScreen"),

    # в DirectorHomeScreen — новый колбэк
    ("""                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },""",
     """                onNavigateToChats = { navController.navigate(Routes.CHATS_LIST) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },"""),

    # новый composable после CREATE_TASK
    ("""        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }""",
     """        composable(Routes.CREATE_TASK) {
            CreateTaskScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_HOMEWORK) {
            CreateHomeworkScreen(
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() }
            )
        }"""),
], skip_if_marker="Routes.CREATE_HOMEWORK")


# ============================================================
# 6. DirectorHomeScreen.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", [
    # добавить колбэк
    ("""    onNavigateToChats: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,""",
     """    onNavigateToChats: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,"""),

    # заменить последний ряд на (Домашка, Чаты)
    ("""            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }""",
     """            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DirectorActionCard(Icons.Default.School, "Домашка", "Задание следопытам", onNavigateToHomework, Modifier.weight(1f))
                DirectorActionCard(Icons.Default.Email, "Чаты", "Личные и группы", onNavigateToChats, Modifier.weight(1f))
            }"""),
], skip_if_marker="onNavigateToHomework")


# ============================================================
# 7. MyTasksScreen.kt — полный rewrite (с табами)
# ============================================================
MY_TASKS = '''package com.pathfinder.hub.ui.screens.tasks

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.ui.screens.homework.HomeworkTabContent
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit,
    onCreateTask: () -> Unit = {},
    viewModel: MyTasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои задачи") },
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
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onCreateTask,
                    containerColor = PathfinderBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, "Создать задачу")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Задачи") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Домашка") }
                )
            }
            when (selectedTab) {
                0 -> TasksTabContent(state, viewModel)
                1 -> HomeworkTabContent()
            }
        }
    }
}

@Composable
private fun TasksTabContent(state: MyTasksUiState, vm: MyTasksViewModel) {
    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }
        state.tasks.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Задач пока нет")
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tasks, key = { it.id }) { t ->
                    TaskCard(t, vm)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, vm: MyTasksViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(task.description, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Статус: ${task.status}",
                style = MaterialTheme.typography.labelLarge,
                color = PathfinderBlue
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status == "assigned") {
                    Button(onClick = { vm.acceptTask(task.id) }) { Text("Принять") }
                }
                if (task.status == "accepted" || task.status == "in_progress") {
                    Button(onClick = { vm.completeTask(task.id) }) { Text("Выполнено") }
                }
            }
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/tasks/MyTasksScreen.kt", MY_TASKS)


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  Директор:")
print("    1. Главная → карточка 'Домашка' (рядом с 'Чаты')")
print("    2. Заполни: название, описание, срок (2026-11-01), приз")
print("    3. 'Назначить всем следопытам' → возврат на главную")
print("  Teen:")
print("    4. Домой → 'Мои задачи' → вкладка 'Домашка'")
print("    5. Видит домашки, кнопка 'Отметить выполненным'")
print("    6. После нажатия — 'Отправлено на проверку'")
print()
print("Откат: каждый файл рядом с .bak.<timestamp>")