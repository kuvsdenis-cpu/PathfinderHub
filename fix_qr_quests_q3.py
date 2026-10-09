#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QR-quests Q3 (director UI) - 09.10.2026

- NEW: ManageQuestsScreen + VM
- NEW: QuestEditorScreen + VM (create/edit, выбор станций, ↑↓)
- PATCH: Routes (QUEST_MANAGE, QUEST_EDITOR)
- PATCH: PathfinderNavHost (2 composable + колбэк)
- PATCH: DirectorHomeScreen (+ряды "Домашка/Проверка" и "QR-квесты")

Запуск:
    python fix_qr_quests_q3.py
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
# 1. ManageQuestsViewModel.kt
# ============================================================
MANAGE_VM = '''package com.pathfinder.hub.ui.qr.quests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestListItem(
    val quest: QrQuestEntity,
    val stepCount: Int,
)

data class ManageQuestsUiState(
    val isLoading: Boolean = true,
    val items: List<QuestListItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class ManageQuestsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrQuestRepository: QrQuestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageQuestsUiState())
    val state: StateFlow<ManageQuestsUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                qrQuestRepository.observeAllQuests(clubId).collect { quests ->
                    val items = quests.map { q ->
                        QuestListItem(q, qrQuestRepository.getStepCount(q.id))
                    }
                    _state.update { it.copy(isLoading = false, items = items) }
                }
            }
        }
    }

    fun deleteQuest(id: String) {
        viewModelScope.launch {
            try { qrQuestRepository.deleteQuest(id) }
            catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/quests/ManageQuestsViewModel.kt", MANAGE_VM)


# ============================================================
# 2. ManageQuestsScreen.kt
# ============================================================
MANAGE_SCREEN = '''package com.pathfinder.hub.ui.qr.quests

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageQuestsScreen(
    onBack: () -> Unit,
    onOpenQuest: (String) -> Unit,
    onCreateQuest: () -> Unit,
    viewModel: ManageQuestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR-квесты") },
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
            FloatingActionButton(
                onClick = onCreateQuest,
                containerColor = PathfinderBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Создать квест")
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEvents, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Пока нет квестов", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Нажмите + чтобы создать первый",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.quest.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenQuest(item.quest.id) }
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents, null,
                                tint = PathfinderBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.quest.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.quest.description.isNotBlank()) {
                                    Text(
                                        item.quest.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("${item.stepCount} станций") }
                                    )
                                    if (item.quest.rewardPoints > 0) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("+${item.quest.rewardPoints}") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Star, null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { deleteId = item.quest.id }) {
                                Icon(
                                    Icons.Default.Delete, "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить квест?") },
            text = { Text("Все связи со станциями и прогресс участников будут потеряны.") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.deleteQuest(id)
                        deleteId = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteId = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/quests/ManageQuestsScreen.kt", MANAGE_SCREEN)


# ============================================================
# 3. QuestEditorViewModel.kt
# ============================================================
EDITOR_VM = '''package com.pathfinder.hub.ui.qr.quests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.qr.QrQuestEntity
import com.pathfinder.hub.data.local.entity.qr.QrStationEntity
import com.pathfinder.hub.data.repository.QrQuestRepository
import com.pathfinder.hub.data.repository.QrStationRepository
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

data class QuestEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val questId: String? = null,
    val title: String = "",
    val description: String = "",
    val rewardPoints: String = "0",
    val rewardAchievementId: String = "",
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val selectedStationIds: List<String> = emptyList(),
    val allStations: List<QrStationEntity> = emptyList(),
    val isLoadingAllStations: Boolean = true,
    val showStationDialog: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class QuestEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val qrQuestRepository: QrQuestRepository,
    private val qrStationRepository: QrStationRepository,
) : ViewModel() {

    private val questIdArg: String? = savedStateHandle.get<String>("questId")
        ?.takeIf { it.isNotBlank() && it != "new" }

    private val _state = MutableStateFlow(QuestEditorUiState())
    val state: StateFlow<QuestEditorUiState> = _state.asStateFlow()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Не авторизован") }
        } else {
            load(uid)
        }
    }

    private fun load(uid: String) {
        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val clubId = user?.clubId
            if (clubId.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }

            // Все станции клуба
            qrStationRepository.observeAllStations(clubId).collect { stations ->
                _state.update { it.copy(allStations = stations, isLoadingAllStations = false) }
            }
        }

        // Если edit — грузим квест
        if (questIdArg != null) {
            viewModelScope.launch {
                val quest = qrQuestRepository.getQuest(questIdArg)
                if (quest == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Квест не найден") }
                    return@launch
                }
                val links = qrQuestRepository.getQuestStations(questIdArg)
                _state.update {
                    it.copy(
                        isLoading = false,
                        isNew = false,
                        questId = quest.id,
                        title = quest.title,
                        description = quest.description,
                        rewardPoints = quest.rewardPoints.toString(),
                        rewardAchievementId = quest.rewardAchievementId ?: "",
                        dateStart = quest.dateStart,
                        dateEnd = quest.dateEnd,
                        selectedStationIds = links.sortedBy { l -> l.stepIndex }.map { l -> l.stationId },
                    )
                }
            }
        } else {
            _state.update { it.copy(isLoading = false, isNew = true) }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onRewardPoints(v: String) = _state.update { it.copy(rewardPoints = v.filter { c -> c.isDigit() }) }
    fun onRewardAchievement(v: String) = _state.update { it.copy(rewardAchievementId = v) }
    fun onDateStart(v: Date) = _state.update { it.copy(dateStart = v) }
    fun onDateEnd(v: Date) = _state.update { it.copy(dateEnd = v) }
    fun clearDates() = _state.update { it.copy(dateStart = null, dateEnd = null) }

    fun showStationDialog() = _state.update { it.copy(showStationDialog = true) }
    fun hideStationDialog() = _state.update { it.copy(showStationDialog = false) }

    fun toggleStation(stationId: String) {
        _state.update {
            val cur = it.selectedStationIds
            val next = if (stationId in cur) cur - stationId else cur + stationId
            it.copy(selectedStationIds = next)
        }
    }

    fun moveUp(stationId: String) {
        _state.update {
            val list = it.selectedStationIds.toMutableList()
            val i = list.indexOf(stationId)
            if (i > 0) {
                val tmp = list[i - 1]
                list[i - 1] = list[i]
                list[i] = tmp
            }
            it.copy(selectedStationIds = list)
        }
    }

    fun moveDown(stationId: String) {
        _state.update {
            val list = it.selectedStationIds.toMutableList()
            val i = list.indexOf(stationId)
            if (i >= 0 && i < list.size - 1) {
                val tmp = list[i + 1]
                list[i + 1] = list[i]
                list[i] = tmp
            }
            it.copy(selectedStationIds = list)
        }
    }

    fun save() {
        val s = _state.value
        val uid = sessionManager.getUserId() ?: return
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.selectedStationIds.isEmpty()) {
            _state.update { it.copy(errorMessage = "Добавьте хотя бы одну станцию") }
            return
        }

        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val user = userRepository.getUser(uid)
                val clubId = user?.clubId
                if (clubId.isNullOrBlank()) {
                    _state.update { it.copy(isSaving = false, errorMessage = "Клуб не найден") }
                    return@launch
                }
                val id = s.questId ?: ("quest_" + UUID.randomUUID().toString().take(8))
                val now = Date()
                val entity = QrQuestEntity(
                    id = id,
                    clubId = clubId,
                    title = s.title.trim(),
                    description = s.description.trim(),
                    type = "linear",
                    rewardPoints = s.rewardPoints.toIntOrNull() ?: 0,
                    rewardAchievementId = s.rewardAchievementId.trim().ifBlank { null },
                    dateStart = s.dateStart,
                    dateEnd = s.dateEnd,
                    status = "active",
                    createdBy = uid,
                    createdAt = now,
                    updatedAt = now,
                    pendingSync = true,
                )
                qrQuestRepository.upsertQuest(entity)
                qrQuestRepository.setQuestStations(id, s.selectedStationIds)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Ошибка") }
            }
        }
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/quests/QuestEditorViewModel.kt", EDITOR_VM)


# ============================================================
# 4. QuestEditorScreen.kt
# ============================================================
EDITOR_SCREEN = '''package com.pathfinder.hub.ui.qr.quests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuestEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Новый квест" else "Квест") },
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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Название *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardPoints,
                onValueChange = viewModel::onRewardPoints,
                label = { Text("Очки за завершение квеста") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardAchievementId,
                onValueChange = viewModel::onRewardAchievement,
                label = { Text("ID ачивки за квест (опционально)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )
            Spacer(Modifier.height(12.dp))

            Text("Срок квеста", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            DateField(
                value = state.dateStart,
                label = "Дата начала (опционально)",
                onValueChange = viewModel::onDateStart,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            DateField(
                value = state.dateEnd,
                label = "Дата окончания (пусто = бессрочный)",
                onValueChange = viewModel::onDateEnd,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.dateStart != null || state.dateEnd != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = viewModel::clearDates) {
                    Text("Сделать бессрочным")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Станции в квесте (порядок прохождения)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Шаг 1 → Шаг 2 → ... Teen должен сканировать по порядку.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (state.selectedStationIds.isEmpty()) {
                Text(
                    "Пока не добавлено ни одной станции",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.selectedStationIds.forEachIndexed { idx, sid ->
                    val station = state.allStations.find { it.id == sid }
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PathfinderBlue
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    station?.name ?: sid,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (station?.description?.isNotBlank() == true) {
                                    Text(
                                        station.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.moveUp(sid) },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, "Выше")
                            }
                            IconButton(
                                onClick = { viewModel.moveDown(sid) },
                                enabled = idx < state.selectedStationIds.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, "Ниже")
                            }
                            IconButton(onClick = { viewModel.toggleStation(sid) }) {
                                Icon(Icons.Default.Close, "Убрать")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::showStationDialog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить станции")
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
                        msg,
                        Modifier.padding(12.dp),
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
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (state.isNew) "Создать квест" else "Сохранить")
                }
            }
        }
    }

    if (state.showStationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideStationDialog,
            title = { Text("Выберите станции") },
            text = {
                if (state.isLoadingAllStations) {
                    CircularProgressIndicator()
                } else if (state.allStations.isEmpty()) {
                    Text("В клубе пока нет станций. Создайте их в разделе «QR-станции».")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.allStations.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = s.id in state.selectedStationIds,
                                    onCheckedChange = { viewModel.toggleStation(s.id) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        s.type + " · " + s.rewardPoints + " очков",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::hideStationDialog) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideStationDialog) {
                    Text("Отмена")
                }
            }
        )
    }
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/qr/quests/QuestEditorScreen.kt", EDITOR_SCREEN)


# ============================================================
# 5. Routes.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/Routes.kt", [
    ("""    const val STAFF = "staff"
    const val MANAGE_PERMISSIONS = "manage_permissions/{userId}"
    fun managePermissions(userId: String) = "manage_permissions/$userId\"""",
     """    const val STAFF = "staff"
    const val MANAGE_PERMISSIONS = "manage_permissions/{userId}"
    fun managePermissions(userId: String) = "manage_permissions/$userId"

    const val QUEST_MANAGE = "quests"
    const val QUEST_EDITOR = "quest_editor/{questId}"
    fun questEditor(questId: String) = "quest_editor/$questId\""""),
], skip_if_marker='const val QUEST_MANAGE')


# ============================================================
# 6. PathfinderNavHost.kt
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/navigation/PathfinderNavHost.kt", [
    ("import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen",
     "import com.pathfinder.hub.ui.screens.clubs.CreateClubScreen\n"
     "import com.pathfinder.hub.ui.qr.quests.ManageQuestsScreen\n"
     "import com.pathfinder.hub.ui.qr.quests.QuestEditorScreen"),

    # колбэк в DirectorHome
    ("""                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToStaff = { navController.navigate(Routes.STAFF) },""",
     """                onNavigateToCreateClub = { navController.navigate(Routes.CREATE_CLUB) },
                onNavigateToStaff = { navController.navigate(Routes.STAFF) },
                onNavigateToQrQuests = { navController.navigate(Routes.QUEST_MANAGE) },
                onNavigateToHomework = { navController.navigate(Routes.CREATE_HOMEWORK) },
                onNavigateToReviewHomework = { navController.navigate(Routes.REVIEW_HOMEWORK) },"""),

    # composable после MANAGE_PERMISSIONS
    ("""        composable(
            route = Routes.MANAGE_PERMISSIONS,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ManagePermissionsScreen(onBack = { navController.popBackStack() })
        }""",
     """        composable(
            route = Routes.MANAGE_PERMISSIONS,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ManagePermissionsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QUEST_MANAGE) {
            ManageQuestsScreen(
                onBack = { navController.popBackStack() },
                onOpenQuest = { questId -> navController.navigate(Routes.questEditor(questId)) },
                onCreateQuest = { navController.navigate(Routes.questEditor("new")) }
            )
        }

        composable(
            route = Routes.QUEST_EDITOR,
            arguments = listOf(navArgument("questId") { type = NavType.StringType })
        ) {
            QuestEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }"""),
], skip_if_marker="Routes.QUEST_MANAGE")


# ============================================================
# 7. DirectorHomeScreen.kt — +2 ряда
# ============================================================
patch("app/src/main/java/com/pathfinder/hub/ui/screens/home/director/DirectorHomeScreen.kt", [
    ("""    onNavigateToQrStations: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,""",
     """    onNavigateToQrStations: () -> Unit = {},
    onNavigateToStaff: () -> Unit = {},
    onNavigateToQrQuests: () -> Unit = {},
    onNavigateToHomework: () -> Unit = {},
    onNavigateToReviewHomework: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,"""),

    ("""                // ---------- Сотрудники (только owner) ----------
                if (u?.isOwnerClub == true) {""",
     """                // ---------- Ряд 7: Домашка + Проверка ----------
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GuardedCard(
                        icon = Icons.Default.Assignment,
                        title = "Домашка",
                        description = "Назначить следопытам",
                        perm = "learning.homework",
                        user = u,
                        onClick = { guard("learning.homework") { onNavigateToHomework() } },
                        modifier = Modifier.weight(1f),
                    )
                    GuardedCard(
                        icon = Icons.Default.FactCheck,
                        title = "Проверка",
                        description = "Сданные домашки",
                        perm = "learning.homework",
                        user = u,
                        onClick = { guard("learning.homework") { onNavigateToReviewHomework() } },
                        modifier = Modifier.weight(1f),
                    )
                }

                // ---------- Ряд 8: QR-квесты + пусто ----------
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
                }

                // ---------- Сотрудники (только owner) ----------
                if (u?.isOwnerClub == true) {"""),
], skip_if_marker="onNavigateToQrQuests")


print()
print("=" * 60)
print("Q3 готово.")
print("=" * 60)
print()
print("Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Что сделано:")
print("  - ManageQuestsScreen + VM: список квестов, удаление, FAB")
print("  - QuestEditorScreen + VM: create/edit, выбор станций (чекбоксы),")
print("      сортировка ↑↓, даты (бессрочный при dateEnd=null)")
print("  - Routes: QUEST_MANAGE, QUEST_EDITOR")
print("  - NavHost: 2 composable + колбэки")
print("  - DirectorHomeScreen: восстановлены 'Домашка' и 'Проверка',")
print("      добавлен 'QR-квесты' (8-й ряд)")
print()
print("Проверка:")
print("  1. Директор → Главная → 'QR-квесты' (8-й ряд)")
print("  2. FAB '+' → форма квеста")
print("  3. 'Добавить станции' → чекбоксы станций клуба")
print("  4. Выбрать 2-3, стрелками ↑↓ задать порядок")
print("  5. 'Создать квест' → в списке появится")
print("  6. Teen → QR → 'Мои квесты' → прогресс 0/N")
print("  7. Teen сканирует станцию шаг 1 → 'Квест X: 1/N'")
print("  8. ... и так до завершения")
print()
print("ВАЖНО: карточки 'Домашка'/'Проверка' восстановлены.")
print("Откат: .bak.<timestamp>")