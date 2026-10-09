#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Date/Time pickers for tasks & homework - 09.10.2026
Запуск:
    python fix_dates_tasks.py
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
# 1. CreateTaskViewModel.kt
# ============================================================
TASK_VM = '''package com.pathfinder.hub.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.repository.TaskRepository
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

data class CreateTaskUiState(
    val title: String = "",
    val description: String = "",
    val assignedTo: String = "",
    val dueDate: Date? = null,
    val priority: String = "normal",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
    val clubMembers: List<UserEntity> = emptyList(),
    val currentUserId: String? = null,
)

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateTaskUiState())
    val state: StateFlow<CreateTaskUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        _state.update { it.copy(currentUserId = userId) }
        loadClubMembers()
    }

    private fun loadClubMembers() {
        val userId = _state.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId
                if (!clubId.isNullOrBlank()) {
                    userRepository.observeClubMembers(clubId).collect { members ->
                        _state.update { it.copy(clubMembers = members) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onAssignedToChange(v: String) = _state.update { it.copy(assignedTo = v) }
    fun onDueDateChange(v: Date) = _state.update { it.copy(dueDate = v) }
    fun onPriorityChange(v: String) = _state.update { it.copy(priority = v) }

    fun createTask() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название задачи") }
            return
        }
        if (s.assignedTo.isBlank()) {
            _state.update { it.copy(errorMessage = "Выберите исполнителя") }
            return
        }
        val directorId = s.currentUserId
        if (directorId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(directorId)
                val clubId = currentUser?.clubId

                val task = TaskEntity(
                    id = UUID.randomUUID().toString(),
                    level = "club",
                    clubId = clubId,
                    eventId = null,
                    title = s.title.trim(),
                    description = s.description.trim(),
                    assignedTo = s.assignedTo,
                    assignedBy = directorId,
                    dueDate = s.dueDate,
                    priority = s.priority,
                    status = "assigned",
                    createdAt = Date(),
                    confirmedBy = null,
                    confirmedAt = null,
                    pendingSync = true,
                )

                taskRepository.upsert(task)
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/tasks/CreateTaskViewModel.kt", TASK_VM)


# ============================================================
# 2. CreateTaskScreen.kt
# ============================================================
TASK_SCREEN = '''package com.pathfinder.hub.ui.screens.tasks

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое задание") },
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
            Text("Параметры задания", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

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

            // Исполнитель
            var expanded by remember { mutableStateOf(false) }
            val assignedUserName = state.clubMembers
                .find { it.id == state.assignedTo }
                ?.let { "${it.firstName} ${it.lastName}" }
                ?: "Выберите исполнителя"

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = assignedUserName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Исполнитель") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    state.clubMembers.forEach { user ->
                        DropdownMenuItem(
                            text = {
                                Text("${user.firstName} ${user.lastName} (${getRoleName(user.role)})")
                            },
                            onClick = {
                                viewModel.onAssignedToChange(user.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Срок — календарь
            DateField(
                value = state.dueDate,
                label = "Срок выполнения (необязательно)",
                onValueChange = viewModel::onDueDateChange,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Приоритет
            var priorityExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { priorityExpanded = !priorityExpanded }
            ) {
                OutlinedTextField(
                    value = when (state.priority) {
                        "high" -> "Высокий"
                        "normal" -> "Обычный"
                        "low" -> "Низкий"
                        else -> state.priority
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Приоритет") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    listOf(
                        "high" to "Высокий",
                        "normal" to "Обычный",
                        "low" to "Низкий",
                    ).forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onPriorityChange(value)
                                priorityExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::createTask,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Назначить задание")
                }
            }
        }
    }
}

private fun getRoleName(role: String): String = when (role) {
    "teen" -> "Следопыт"
    "parent" -> "Родитель"
    "instructor" -> "Наставник"
    "director" -> "Директор"
    "secretary" -> "Секретарь"
    "conference" -> "Конференция"
    else -> role
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/tasks/CreateTaskScreen.kt", TASK_SCREEN)


# ============================================================
# 3. CreateHomeworkViewModel.kt
# ============================================================
HW_VM = '''package com.pathfinder.hub.ui.screens.homework

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
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateHomeworkUiState(
    val title: String = "",
    val description: String = "",
    val dueDate: Date? = null,
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
    fun onDueDateChange(v: Date) = _state.update { it.copy(dueDate = v) }
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
        if (s.dueDate == null) {
            _state.update { it.copy(errorMessage = "Укажите срок") }
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
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "В клубе нет следопытов")
                    }
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
                            dueDate = s.dueDate,
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/homework/CreateHomeworkViewModel.kt", HW_VM)


# ============================================================
# 4. CreateHomeworkScreen.kt
# ============================================================
HW_SCREEN = '''package com.pathfinder.hub.ui.screens.homework

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
import com.pathfinder.hub.ui.components.DateField
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

            DateField(
                value = state.dueDate,
                label = "Срок",
                onValueChange = viewModel::onDueDateChange,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
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
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/homework/CreateHomeworkScreen.kt", HW_SCREEN)


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Директор → Задания → 'Срок выполнения' → календарь")
print("  2. Формат: dd.MM.yyyy")
print("  3. Директор → Домашка → 'Срок' → календарь")
print("  4. Создание события и отчёта тоже работают (из fix_dates.py)")
print()
print("Откат: .bak.<timestamp>")