#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — Сессия B+C.
Следопыт (материалы + задания) + редактор (директор + флаг наставника).
Создаёт 10 файлов + патчит 2.

Запуск из корня проекта (где settings.gradle.kts):
    python3 setup_learning_bc.py
"""

import shutil
import sys
from pathlib import Path


# ============================================================
# ПОИСК КОРНЯ
# ============================================================
def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта (settings.gradle.kts).")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"

if not PKG.exists():
    print(f"❌ Не найден пакет: {PKG}")
    sys.exit(1)


# ============================================================
# НОВЫЕ ФАЙЛЫ
# ============================================================
FILES = {}

# =========================================================================
# B.1 — RequirementContentViewModel.kt
# =========================================================================
FILES["ui/learning/RequirementContentViewModel.kt"] = '''package com.pathfinder.hub.ui.learning

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.repository.RequirementContentRepository
import com.pathfinder.hub.data.repository.TaskAnswerRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.learning.SubmitTaskAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние секции «Материалы и задания» на экране требования.
 */
data class RequirementContentUiState(
    val isLoading: Boolean = true,
    val materials: List<RequirementMaterialEntity> = emptyList(),
    val tasks: List<RequirementTaskEntity> = emptyList(),
    val answers: Map<String, RequirementTaskAnswerEntity> = emptyMap(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    /** Сколько обязательных заданий выполнено (approved/auto_approved). */
    val completedRequiredCount: Int
        get() = tasks.count { t ->
            t.isRequired && answers[t.id]?.let {
                it.status in RequirementTaskAnswerEntity.DONE_STATUSES
            } == true
        }

    val requiredCount: Int get() = tasks.count { it.isRequired }

    val allRequiredDone: Boolean
        get() = requiredCount == 0 || completedRequiredCount >= requiredCount

    val hasRequiredTasks: Boolean get() = requiredCount > 0
}

@HiltViewModel
class RequirementContentViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val contentRepo: RequirementContentRepository,
    private val answerRepo: TaskAnswerRepository,
    private val submitUseCase: SubmitTaskAnswerUseCase,
) : ViewModel() {

    private val levelId: String = savedStateHandle.get<String>("levelId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(RequirementContentUiState())
    val state: StateFlow<RequirementContentUiState> = _state.asStateFlow()

    private var clubId: String? = null
    private var userId: String? = null

    init {
        load()
    }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null || requirementId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет данных требования") }
            return
        }
        userId = uid

        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val cid = user?.clubId
            if (cid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            clubId = cid

            // Материалы
            launch {
                contentRepo.observeMaterials(cid, requirementId)
                    .catch { e -> Log.e(TAG, "materials flow error", e) }
                    .collect { list ->
                        _state.update { it.copy(materials = list, isLoading = false) }
                    }
            }

            // Задания
            launch {
                contentRepo.observeTasks(cid, requirementId)
                    .catch { e -> Log.e(TAG, "tasks flow error", e) }
                    .collect { list ->
                        _state.update { it.copy(tasks = list.sortedBy { t -> t.orderIndex }) }
                    }
            }

            // Ответы
            launch {
                answerRepo.observeMyAnswers(cid, uid)
                    .catch { e -> Log.e(TAG, "answers flow error", e) }
                    .collect { list ->
                        _state.update { s ->
                            s.copy(answers = list.associateBy { it.taskId })
                        }
                    }
            }
        }
    }

    // ==================== ОТВЕТЫ ====================

    fun saveDraft(task: RequirementTaskEntity, answer: String, attachments: List<String>) {
        val cid = clubId ?: return
        val uid = userId ?: return
        viewModelScope.launch {
            val r = answerRepo.saveDraft(
                clubId = cid, userId = uid, requirementId = requirementId,
                taskId = task.id, answer = answer, attachments = attachments
            )
            if (r.isSuccess) {
                _state.update { it.copy(infoMessage = "Черновик сохранён") }
            } else {
                _state.update { it.copy(errorMessage = r.exceptionOrNull()?.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun submit(task: RequirementTaskEntity, answer: String, attachments: List<String>) {
        val cid = clubId ?: return
        val uid = userId ?: return
        viewModelScope.launch {
            val r = submitUseCase(
                clubId = cid, userId = uid, requirementId = requirementId,
                taskId = task.id, answer = answer, attachments = attachments,
                autoCheck = task.autoCheck, correctAnswer = task.correctAnswer
            )
            r.fold(
                onSuccess = { entity ->
                    val msg = if (entity.status == RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED)
                        "Ответ верный, задание зачтено"
                    else
                        "Отправлено на проверку"
                    _state.update { it.copy(infoMessage = msg) }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message ?: "Ошибка отправки") }
                }
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    companion object {
        private const val TAG = "ReqContentVM"
    }
}
'''

# =========================================================================
# B.2 — MaterialItem.kt
# =========================================================================
FILES["ui/learning/MaterialItem.kt"] = '''package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity

@Composable
fun MaterialItem(
    material: RequirementMaterialEntity,
    onOpenLink: (String) -> Unit = {},
    onOpenFile: (String) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (material.type) {
                        "link" -> Icons.Default.Link
                        "file" -> Icons.Default.InsertDriveFile
                        "image" -> Icons.Default.Image
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = material.title.ifBlank { material.type.replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            when (material.type) {
                "text" -> {
                    Text(
                        text = material.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                "link" -> {
                    Text(
                        text = material.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenLink(material.content) }
                    )
                }
                "image" -> {
                    AsyncImage(
                        model = material.content,
                        contentDescription = material.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                "file" -> {
                    OutlinedButton(
                        onClick = { onOpenFile(material.content) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Открыть файл")
                    }
                }
                else -> {
                    Text(text = material.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
'''

# =========================================================================
# B.3 — TaskCard.kt
# =========================================================================
FILES["ui/learning/TaskCard.kt"] = '''package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Composable
fun TaskCard(
    task: RequirementTaskEntity,
    answer: RequirementTaskAnswerEntity?,
    onOpenAnswer: () -> Unit,
) {
    val status = answer?.status ?: RequirementTaskAnswerEntity.STATUS_DRAFT
    val (icon, tint, label) = when (status) {
        RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED -> Triple(Icons.Default.CheckCircle, PathfinderGreen, "Автозачёт")
        RequirementTaskAnswerEntity.STATUS_APPROVED -> Triple(Icons.Default.CheckCircle, PathfinderGreen, "Принято")
        RequirementTaskAnswerEntity.STATUS_REJECTED -> Triple(Icons.Default.Close, PathfinderRed, "Отклонено")
        RequirementTaskAnswerEntity.STATUS_SUBMITTED -> Triple(Icons.Default.Pending, MaterialTheme.colorScheme.primary, "На проверке")
        else -> Triple(Icons.Default.Edit, MaterialTheme.colorScheme.onSurfaceVariant, if (answer != null) "Черновик" else "Не начато")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title.ifBlank { task.type },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!task.isRequired) {
                        Text(
                            "Необязательное",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint
                )
            }

            if (!task.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (status == RequirementTaskAnswerEntity.STATUS_REJECTED && !answer?.reviewComment.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Комментарий: ${answer?.reviewComment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PathfinderRed
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onOpenAnswer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (status) {
                        RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED,
                        RequirementTaskAnswerEntity.STATUS_APPROVED -> "Посмотреть ответ"
                        RequirementTaskAnswerEntity.STATUS_SUBMITTED -> "Изменить ответ"
                        RequirementTaskAnswerEntity.STATUS_REJECTED -> "Исправить"
                        else -> if (answer != null) "Продолжить" else "Ответить"
                    }
                )
            }
        }
    }
}
'''

# =========================================================================
# B.4 — TaskAnswerSheet.kt
# =========================================================================
FILES["ui/learning/TaskAnswerSheet.kt"] = '''package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.ui.common.AttachmentPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskAnswerSheet(
    task: RequirementTaskEntity,
    existingAnswer: RequirementTaskAnswerEntity?,
    onDismiss: () -> Unit,
    onSaveDraft: (String, List<String>) -> Unit,
    onSubmit: (String, List<String>) -> Unit,
    onAttachFile: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var answer by remember { mutableStateOf(existingAnswer?.answer ?: "") }
    val attachments = remember { mutableStateListOf<String>().apply {
        existingAnswer?.attachments?.let { addAll(it) }
    } }

    val isReadOnly = existingAnswer?.status in listOf(
        RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED,
        RequirementTaskAnswerEntity.STATUS_APPROVED
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = task.title.ifBlank { task.type },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (!task.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            when (task.type) {
                "single_choice" -> {
                    task.options.forEachIndexed { idx, opt ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = answer == idx.toString(),
                                onClick = { if (!isReadOnly) answer = idx.toString() }
                            )
                            Text(opt)
                        }
                    }
                }
                "multi_choice" -> {
                    val selected = answer.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                    task.options.forEachIndexed { idx, opt ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.contains(idx),
                                onCheckedChange = { checked ->
                                    if (isReadOnly) return@Checkbox
                                    val ns = selected.toMutableSet()
                                    if (checked) ns.add(idx) else ns.remove(idx)
                                    answer = ns.sorted().joinToString(",")
                                }
                            )
                            Text(opt)
                        }
                    }
                }
                "checkbox" -> {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = answer == "true",
                            onCheckedChange = { if (!isReadOnly) answer = it.toString() }
                        )
                        Text("Подтверждаю")
                    }
                }
                "number" -> {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { if (!isReadOnly) answer = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Число") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isReadOnly,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "long_text" -> {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { if (!isReadOnly) answer = it },
                        label = { Text("Ответ") },
                        minLines = 4,
                        enabled = !isReadOnly,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "file" -> {
                    Text("Прикрепите файл к ответу.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { if (!isReadOnly) answer = it },
                        label = { Text("Ответ") },
                        enabled = !isReadOnly,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (attachments.isNotEmpty()) {
                attachments.forEach { url ->
                    AttachmentPreview(url = url)
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (!isReadOnly) {
                OutlinedButton(onClick = onAttachFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Прикрепить файл")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!isReadOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveDraft(answer, attachments.toList()) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Черновик") }

                    Button(
                        onClick = { onSubmit(answer, attachments.toList()) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Отправить") }
                }
            } else {
                Text(
                    text = "Задание уже принято",
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.pathfinder.hub.ui.theme.PathfinderGreen
                )
            }
        }
    }
}
'''

# =========================================================================
# B.5 — RequirementContentSection.kt
# =========================================================================
FILES["ui/learning/RequirementContentSection.kt"] = '''package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity

/**
 * Секция «Материалы и задания» для экрана требования.
 *
 * Вызывается из RequirementDetailScreen. Возвращает наружу
 * флаг allRequiredDone через onCompletionChanged — чтобы
 * родитель мог заблокировать кнопку «Отметить как выполненное».
 */
@Composable
fun RequirementContentSection(
    onCompletionChanged: (hasRequired: Boolean, allDone: Boolean) -> Unit = { _, _ -> },
    viewModel: RequirementContentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var openTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }

    LaunchedEffect(state.hasRequiredTasks, state.allRequiredDone) {
        onCompletionChanged(state.hasRequiredTasks, state.allRequiredDone)
    }

    if (state.materials.isEmpty() && state.tasks.isEmpty()) {
        return
    }

    Column(Modifier.fillMaxWidth()) {
        // ==================== МАТЕРИАЛЫ ====================
        if (state.materials.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Материалы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            state.materials.forEach { m ->
                MaterialItem(
                    material = m,
                    onOpenLink = { /* TODO: открыть в браузере через Intent */ },
                    onOpenFile = { /* TODO: открыть файл */ }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ==================== ЗАДАНИЯ ====================
        if (state.tasks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Проверочные задания",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${state.completedRequiredCount} из ${state.requiredCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.requiredCount > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.completedRequiredCount.toFloat() / state.requiredCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Spacer(Modifier.height(8.dp))

            state.tasks.forEach { t ->
                TaskCard(
                    task = t,
                    answer = state.answers[t.id],
                    onOpenAnswer = { openTask = t }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ==================== Snackbar-сообщения ====================
        LaunchedEffect(state.infoMessage) {
            state.infoMessage?.let {
                // Можно вынести в SnackbarHost родителя
                viewModel.clearMessages()
            }
        }
    }

    openTask?.let { t ->
        TaskAnswerSheet(
            task = t,
            existingAnswer = state.answers[t.id],
            onDismiss = { openTask = null },
            onSaveDraft = { answer, atts ->
                viewModel.saveDraft(t, answer, atts)
                openTask = null
            },
            onSubmit = { answer, atts ->
                viewModel.submit(t, answer, atts)
                openTask = null
            },
            onAttachFile = {
                // TODO: вынести через колбэк наверх в навигацию
            }
        )
    }
}
'''

# =========================================================================
# C.1 — RequirementEditorViewModel.kt
# =========================================================================
FILES["ui/learning/editor/RequirementEditorViewModel.kt"] = '''package com.pathfinder.hub.ui.learning.editor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.repository.RequirementContentRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequirementEditorUiState(
    val isLoading: Boolean = true,
    val materials: List<RequirementMaterialEntity> = emptyList(),
    val tasks: List<RequirementTaskEntity> = emptyList(),
    val userRole: String = "",
    val canEdit: Boolean = false,
    val canFlag: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class RequirementEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val contentRepo: RequirementContentRepository,
) : ViewModel() {

    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(RequirementEditorUiState())
    val state: StateFlow<RequirementEditorUiState> = _state.asStateFlow()

    private var clubId: String? = null
    private var userId: String? = null

    init { load() }

    private fun load() {
        val uid = sessionManager.getUserId()
        if (uid == null || requirementId.isBlank()) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет данных") }
            return
        }
        userId = uid

        viewModelScope.launch {
            val user = userRepository.getUser(uid)
            val cid = user?.clubId
            if (cid.isNullOrBlank()) {
                _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                return@launch
            }
            clubId = cid
            val role = user.role
            val canEdit = role == "director" || role == "conference_admin"
            val canFlag = !canEdit && role in listOf("instructor", "secretary")

            _state.update { it.copy(userRole = role, canEdit = canEdit, canFlag = canFlag) }

            launch {
                contentRepo.observeMaterials(cid, requirementId).collect { list ->
                    _state.update { it.copy(materials = list.sortedBy { m -> m.orderIndex }, isLoading = false) }
                }
            }
            launch {
                contentRepo.observeTasks(cid, requirementId).collect { list ->
                    _state.update { it.copy(tasks = list.sortedBy { t -> t.orderIndex }) }
                }
            }
        }
    }

    // ==================== CRUD: MATERIALS ====================

    fun saveMaterial(
        existing: RequirementMaterialEntity?,
        type: String, title: String, content: String,
        orderIndex: Int,
    ) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canEdit) return

        viewModelScope.launch {
            val r = if (existing == null) {
                contentRepo.createMaterial(cid, requirementId, uid, type, title, content, orderIndex)
            } else {
                contentRepo.saveMaterial(
                    existing.copy(type = type, title = title, content = content, orderIndex = orderIndex)
                )
            }
            r.fold(
                onSuccess = { _state.update { it.copy(infoMessage = "Сохранено") } },
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message) } }
            )
        }
    }

    fun deleteMaterial(m: RequirementMaterialEntity) {
        val cid = clubId ?: return
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            contentRepo.deleteMaterial(cid, requirementId, m.id)
        }
    }

    // ==================== CRUD: TASKS ====================

    fun saveTask(
        existing: RequirementTaskEntity?,
        type: String, title: String, description: String?,
        options: List<String>, correctAnswer: String?,
        isRequired: Boolean, orderIndex: Int,
    ) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canEdit) return

        viewModelScope.launch {
            val r = if (existing == null) {
                contentRepo.createTask(
                    cid, requirementId, uid, type, title, description,
                    options, correctAnswer, isRequired, orderIndex
                )
            } else {
                contentRepo.saveTask(
                    existing.copy(
                        type = type, title = title, description = description,
                        options = options, correctAnswer = correctAnswer,
                        isRequired = isRequired, orderIndex = orderIndex,
                        autoCheck = RequirementTaskEntity.isAutoCheckType(type)
                    )
                )
            }
            r.fold(
                onSuccess = { _state.update { it.copy(infoMessage = "Сохранено") } },
                onFailure = { e -> _state.update { it.copy(errorMessage = e.message) } }
            )
        }
    }

    fun deleteTask(t: RequirementTaskEntity) {
        val cid = clubId ?: return
        if (!_state.value.canEdit) return
        viewModelScope.launch { contentRepo.deleteTask(cid, requirementId, t.id) }
    }

    // ==================== FLAG (наставник) ====================

    fun flagMaterial(m: RequirementMaterialEntity, comment: String?) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch {
            contentRepo.flagMaterial(cid, requirementId, m.id, uid, comment)
        }
    }

    fun unflagMaterial(m: RequirementMaterialEntity) {
        val cid = clubId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch { contentRepo.unflagMaterial(cid, requirementId, m.id) }
    }

    fun flagTask(t: RequirementTaskEntity, comment: String?) {
        val cid = clubId ?: return
        val uid = userId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch { contentRepo.flagTask(cid, requirementId, t.id, uid, comment) }
    }

    fun unflagTask(t: RequirementTaskEntity) {
        val cid = clubId ?: return
        if (!_state.value.canFlag) return
        viewModelScope.launch { contentRepo.unflagTask(cid, requirementId, t.id) }
    }

    fun clearMessages() = _state.update { it.copy(errorMessage = null, infoMessage = null) }

    companion object { private const val TAG = "ReqEditorVM" }
}
'''

# =========================================================================
# C.2 — MaterialEditDialog.kt
# =========================================================================
FILES["ui/learning/editor/MaterialEditDialog.kt"] = '''package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity

@Composable
fun MaterialEditDialog(
    existing: RequirementMaterialEntity?,
    onDismiss: () -> Unit,
    onSave: (type: String, title: String, content: String) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: "text") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }

    val types = listOf("text", "link", "file", "image")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новый материал" else "Редактировать материал") },
        text = {
            Column {
                Text("Тип", style = MaterialTheme.typography.labelMedium)
                Row {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = {
                        Text(
                            when (type) {
                                "link" -> "URL"
                                "image" -> "URL картинки"
                                "file" -> "URL файла"
                                else -> "Текст"
                            }
                        )
                    },
                    minLines = if (type == "text") 4 else 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && content.isNotBlank(),
                onClick = { onSave(type, title, content) }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
'''

# =========================================================================
# C.3 — TaskEditDialog.kt
# =========================================================================
FILES["ui/learning/editor/TaskEditDialog.kt"] = '''package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity

@Composable
fun TaskEditDialog(
    existing: RequirementTaskEntity?,
    onDismiss: () -> Unit,
    onSave: (
        type: String, title: String, description: String?,
        options: List<String>, correctAnswer: String?,
        isRequired: Boolean
    ) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: "short_text") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var options by remember { mutableStateOf(existing?.options?.joinToString("\\n") ?: "") }
    var correctAnswer by remember { mutableStateOf(existing?.correctAnswer ?: "") }
    var isRequired by remember { mutableStateOf(existing?.isRequired ?: true) }

    val types = listOf(
        "short_text", "long_text", "single_choice", "multi_choice",
        "number", "date", "checkbox", "file", "cipher", "crossword"
    )
    val isChoice = type == "single_choice" || type == "multi_choice"
    val isAuto = RequirementTaskEntity.isAutoCheckType(type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новое задание" else "Редактировать задание") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Тип", style = MaterialTheme.typography.labelMedium)
                types.chunked(3).forEach { row ->
                    Row {
                        row.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isChoice) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = options, onValueChange = { options = it },
                        label = { Text("Варианты (по одному в строке)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isAuto) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctAnswer, onValueChange = { correctAnswer = it },
                        label = {
                            Text(
                                if (isChoice) "Индекс правильного (0 = первый)"
                                else "Правильный ответ"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    Checkbox(checked = isRequired, onCheckedChange = { isRequired = it })
                    Text("Обязательное", modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        type, title, description.ifBlank { null },
                        options.split("\\n").map { it.trim() }.filter { it.isNotBlank() },
                        correctAnswer.ifBlank { null },
                        isRequired
                    )
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
'''

# =========================================================================
# C.4 — FlagDialog.kt
# =========================================================================
FILES["ui/learning/editor/FlagDialog.kt"] = '''package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FlagDialog(
    targetTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пометить для правки") },
        text = {
            Column {
                Text("Что не так с «$targetTitle»?")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text("Комментарий (необязательно)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(comment.ifBlank { null }) }) { Text("Пометить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
'''

# =========================================================================
# C.5 — RequirementEditorScreen.kt
# =========================================================================
FILES["ui/learning/editor/RequirementEditorScreen.kt"] = '''package com.pathfinder.hub.ui.learning.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirementEditorScreen(
    onBack: () -> Unit,
    viewModel: RequirementEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    var editingMaterial by remember { mutableStateOf<RequirementMaterialEntity?>(null) }
    var creatingMaterial by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }
    var creatingTask by remember { mutableStateOf(false) }
    var flaggingMaterial by remember { mutableStateOf<RequirementMaterialEntity?>(null) }
    var flaggingTask by remember { mutableStateOf<RequirementTaskEntity?>(null) }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор требования") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (tab == 0) creatingMaterial = true else creatingTask = true
                    },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(if (tab == 0) "Материал" else "Задание") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Материалы (${state.materials.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Задания (${state.tasks.size})") })
            }

            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(state.materials, key = { it.id }) { m ->
                        RowItemMaterial(
                            m = m,
                            canEdit = state.canEdit,
                            canFlag = state.canFlag,
                            onEdit = { editingMaterial = m },
                            onDelete = { viewModel.deleteMaterial(m) },
                            onFlag = { flaggingMaterial = m },
                            onUnflag = { viewModel.unflagMaterial(m) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(state.tasks, key = { it.id }) { t ->
                        RowItemTask(
                            t = t,
                            canEdit = state.canEdit,
                            canFlag = state.canFlag,
                            onEdit = { editingTask = t },
                            onDelete = { viewModel.deleteTask(t) },
                            onFlag = { flaggingTask = t },
                            onUnflag = { viewModel.unflagTask(t) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // ---------------- MATERIAL DIALOGS ----------------
    if (creatingMaterial) {
        MaterialEditDialog(
            existing = null,
            onDismiss = { creatingMaterial = false },
            onSave = { type, title, content ->
                viewModel.saveMaterial(null, type, title, content, state.materials.size)
                creatingMaterial = false
            }
        )
    }
    editingMaterial?.let { m ->
        MaterialEditDialog(
            existing = m,
            onDismiss = { editingMaterial = null },
            onSave = { type, title, content ->
                viewModel.saveMaterial(m, type, title, content, m.orderIndex)
                editingMaterial = null
            }
        )
    }
    flaggingMaterial?.let { m ->
        FlagDialog(
            targetTitle = m.title,
            onDismiss = { flaggingMaterial = null },
            onConfirm = { c -> viewModel.flagMaterial(m, c); flaggingMaterial = null }
        )
    }

    // ---------------- TASK DIALOGS ----------------
    if (creatingTask) {
        TaskEditDialog(
            existing = null,
            onDismiss = { creatingTask = false },
            onSave = { type, title, description, options, correct, required ->
                viewModel.saveTask(null, type, title, description, options, correct, required, state.tasks.size)
                creatingTask = false
            }
        )
    }
    editingTask?.let { t ->
        TaskEditDialog(
            existing = t,
            onDismiss = { editingTask = null },
            onSave = { type, title, description, options, correct, required ->
                viewModel.saveTask(t, type, title, description, options, correct, required, t.orderIndex)
                editingTask = null
            }
        )
    }
    flaggingTask?.let { t ->
        FlagDialog(
            targetTitle = t.title,
            onDismiss = { flaggingTask = null },
            onConfirm = { c -> viewModel.flagTask(t, c); flaggingTask = null }
        )
    }
}

@Composable
private fun RowItemMaterial(
    m: RequirementMaterialEntity,
    canEdit: Boolean, canFlag: Boolean,
    onEdit: () -> Unit, onDelete: () -> Unit,
    onFlag: () -> Unit, onUnflag: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("[${m.type}] ${m.title}", style = MaterialTheme.typography.titleMedium)
                if (m.flaggedForEdit) {
                    Text("Помечено: ${m.flagComment ?: "без комментария"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (canEdit) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
            if (canFlag) {
                IconButton(onClick = if (m.flaggedForEdit) onUnflag else onFlag) {
                    Icon(Icons.Default.Flag, null,
                        tint = if (m.flaggedForEdit) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RowItemTask(
    t: RequirementTaskEntity,
    canEdit: Boolean, canFlag: Boolean,
    onEdit: () -> Unit, onDelete: () -> Unit,
    onFlag: () -> Unit, onUnflag: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("[${t.type}] ${t.title}", style = MaterialTheme.typography.titleMedium)
                if (!t.isRequired) Text("Необязательное", style = MaterialTheme.typography.labelSmall)
                if (t.flaggedForEdit) {
                    Text("Помечено: ${t.flagComment ?: "без комментария"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (canEdit) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
            if (canFlag) {
                IconButton(onClick = if (t.flaggedForEdit) onUnflag else onFlag) {
                    Icon(Icons.Default.Flag, null,
                        tint = if (t.flaggedForEdit) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
'''


# ============================================================
# ЗАПИСЬ ФАЙЛОВ
# ============================================================
def write_files() -> None:
    print(f"📁 Корень проекта: {ROOT}")
    print(f"📁 Пакет:          {PKG}\n")

    created, replaced = 0, 0
    for rel, content in FILES.items():
        target = PKG / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            shutil.copy2(target, target.with_suffix(target.suffix + ".bak"))
            print(f"⚠️  Перезаписываю {rel} (.bak)")
            replaced += 1
        target.write_text(content, encoding="utf-8")
        print(f"✅ {rel}")
        created += 1
    print(f"\n📦 Создано: {created}, бэкапов: {replaced}")


# ============================================================
# ПАТЧ 1 — RequirementDetailScreen.kt
# ============================================================
def patch_requirement_detail_screen() -> bool:
    print("\n🔧 Патчу RequirementDetailScreen.kt...")
    f = PKG / "ui/screens/levels/RequirementDetailScreen.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "RequirementContentSection" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Импорты
    import_anchor = "import com.pathfinder.hub.ui.common.AttachmentPreview"
    if import_anchor not in text:
        print(f"❌ Нет якоря импорта: {import_anchor}")
        return False

    new_imports = (
        "import com.pathfinder.hub.ui.learning.RequirementContentSection\n"
        "import androidx.compose.material.icons.filled.Edit\n"
    )
    text = text.replace(import_anchor, new_imports + import_anchor, 1)

    # 2) TopAppBar: добавить кнопку «Редактировать» для директора (упрощённо — показываем всегда,
    #    VM сам проверит права). Точнее — проверим права в VM. Здесь просто action.
    top_bar_anchor = '''                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )'''
    if top_bar_anchor not in text:
        print("❌ Не найден якорь TopAppBar")
        return False

    top_bar_new = '''                actions = {
                    IconButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )'''
    text = text.replace(top_bar_anchor, top_bar_new, 1)

    # 3) Состояния: showEditor, hasRequiredTasks, allRequiredDone
    state_anchor = '''    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }'''
    if state_anchor not in text:
        print("❌ Не найден якорь состояний")
        return False

    state_new = '''    var showConfirmDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var hasRequiredTasks by remember { mutableStateOf(false) }
    var allRequiredDone by remember { mutableStateOf(true) }'''
    text = text.replace(state_anchor, state_new, 1)

    # 4) Врезка RequirementContentSection после StatusCard
    insertion_anchor = '''                    StatusCard(state.status, state.comment)
'''
    if insertion_anchor not in text:
        print("❌ Не найден якорь StatusCard")
        return False

    insertion = '''                    StatusCard(state.status, state.comment)

                    RequirementContentSection(
                        onCompletionChanged = { hasReq, allDone ->
                            hasRequiredTasks = hasReq
                            allRequiredDone = allDone
                        }
                    )
'''
    text = text.replace(insertion_anchor, insertion, 1)

    # 5) B2: кнопка «Отметить как выполненное» — блокировать
    old_btn_cond = '''                        "not_started", "rejected" -> {
                            Button(
                                onClick = { showConfirmDialog = true },
                                enabled = !state.isSaving,'''
    new_btn_cond = '''                        "not_started", "rejected" -> {
                            val canSubmit = !hasRequiredTasks || allRequiredDone
                            Button(
                                onClick = { showConfirmDialog = true },
                                enabled = !state.isSaving && canSubmit,'''
    if old_btn_cond not in text:
        print("❌ Не найден якорь кнопки 'Отметить как выполненное'")
        return False
    text = text.replace(old_btn_cond, new_btn_cond, 1)

    # 6) Подсказка под кнопкой (если задания не все сданы)
    hint_anchor = '''                                } else {
                                    Text("Отметить как выполненное")
                                }
                            }
                        }'''
    hint_new = '''                                } else {
                                    Text(
                                        if (!hasRequiredTasks || allRequiredDone)
                                            "Отметить как выполненное"
                                        else
                                            "Сначала выполните все задания"
                                    )
                                }
                            }
                            if (hasRequiredTasks && !allRequiredDone) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Все обязательные задания должны быть проверены и приняты.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }'''
    if hint_anchor not in text:
        print("❌ Не найден якорь подсказки у кнопки")
        return False
    text = text.replace(hint_anchor, hint_new, 1)

    # 7) Открытие редактора через showEditor → навигация. Но onOpenEditor нет в параметрах.
    #    Допишем параметр в сигнатуру.
    sig_anchor = '''fun RequirementDetailScreen(
    onBack: () -> Unit,
    onUploadReport: (String, String) -> Unit,
    savedStateHandle: SavedStateHandle,
    viewModel: RequirementDetailViewModel = hiltViewModel()
) {'''
    sig_new = '''fun RequirementDetailScreen(
    onBack: () -> Unit,
    onUploadReport: (String, String) -> Unit,
    onOpenEditor: (String, String) -> Unit = { _, _ -> },
    savedStateHandle: SavedStateHandle,
    viewModel: RequirementDetailViewModel = hiltViewModel()
) {'''
    if sig_anchor not in text:
        print("❌ Не найден якорь сигнатуры composable")
        return False
    text = text.replace(sig_anchor, sig_new, 1)

    # 8) LaunchedEffect(showEditor) → вызываем onOpenEditor
    dialog_anchor = '''    if (showConfirmDialog) {'''
    if dialog_anchor not in text:
        print("❌ Не найден якорь диалогов")
        return False

    editor_effect = '''    LaunchedEffect(showEditor) {
        if (showEditor) {
            val lid = viewModel.getLevelId()
            val rid = state.requirement?.id
            if (rid != null) {
                onOpenEditor(lid, rid)
            }
            showEditor = false
        }
    }

    if (showConfirmDialog) {'''
    text = text.replace(dialog_anchor, editor_effect, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementDetailScreen.kt пропатчен (.bak)")
    return True


# ============================================================
# ПАТЧ 2 — PathfinderNavHost.kt
# ============================================================
def patch_navhost() -> bool:
    print("\n🔧 Патчу PathfinderNavHost.kt...")
    f = PKG / "ui/navigation/PathfinderNavHost.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "RequirementEditorScreen" in text:
        print("⚠️  Уже пропатчен")
        return True

    # 1) Импорт
    import_anchor = "import com.pathfinder.hub.ui.screens.levels.RequirementDetailScreen"
    if import_anchor not in text:
        print("❌ Нет якоря импорта RequirementDetailScreen")
        return False
    text = text.replace(
        import_anchor,
        import_anchor + "\nimport com.pathfinder.hub.ui.learning.editor.RequirementEditorScreen",
        1
    )

    # 2) В composable REQUIREMENT_DETAIL добавить onOpenEditor
    old_call = '''            RequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onUploadReport = { lvlId, requirementId ->
                    navController.navigate(
                        Routes.fileUpload(
                            contextType = "LEVEL_REQUIREMENT",
                            contextId = requirementId
                        )
                    )
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )'''
    new_call = '''            RequirementDetailScreen(
                onBack = { navController.popBackStack() },
                onUploadReport = { lvlId, requirementId ->
                    navController.navigate(
                        Routes.fileUpload(
                            contextType = "LEVEL_REQUIREMENT",
                            contextId = requirementId
                        )
                    )
                },
                onOpenEditor = { lvlId, requirementId ->
                    navController.navigate(Routes.requirementEditor(lvlId, requirementId))
                },
                savedStateHandle = backStackEntry.savedStateHandle
            )'''
    if old_call not in text:
        print("❌ Не найден якорь вызова RequirementDetailScreen")
        return False
    text = text.replace(old_call, new_call, 1)

    # 3) Новый composable — редактор
    anchor = "        // === Teen - Honors ==="
    if anchor not in text:
        print("❌ Не найден якорь '// === Teen - Honors ==='")
        return False

    editor_block = '''        composable(
            route = Routes.REQUIREMENT_EDITOR,
            arguments = listOf(
                navArgument("levelId") { type = NavType.StringType },
                navArgument("requirementId") { type = NavType.StringType }
            )
        ) {
            RequirementEditorScreen(onBack = { navController.popBackStack() })
        }

'''
    text = text.replace(anchor, editor_block + anchor, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ PathfinderNavHost.kt пропатчен (.bak)")
    return True


# ============================================================
# ПАТЧ 3 — Routes.kt (добавить REQUIREMENT_EDITOR)
# ============================================================
def patch_routes() -> bool:
    print("\n🔧 Патчу Routes.kt...")
    f = PKG / "ui/navigation/Routes.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "REQUIREMENT_EDITOR" in text:
        print("⚠️  Уже пропатчен")
        return True

    anchor = 'const val REQUIREMENT_DETAIL = "requirement_detail/{levelId}/{requirementId}"'
    if anchor not in text:
        print("❌ Не найден якорь REQUIREMENT_DETAIL")
        return False

    new_line = '''    const val REQUIREMENT_EDITOR = "requirement_editor/{levelId}/{requirementId}"
    fun requirementEditor(levelId: String, requirementId: String) =
        "requirement_editor/$levelId/$requirementId"

'''
    text = text.replace(anchor, anchor + "\n" + new_line, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ Routes.kt пропатчен (.bak)")
    return True


# ============================================================
# MAIN
# ============================================================
def main():
    print("=" * 60)
    print("  Pathfinder Hub — Сессия B+C")
    print("=" * 60)

    write_files()

    results = {
        "RequirementDetailScreen.kt": patch_requirement_detail_screen(),
        "PathfinderNavHost.kt": patch_navhost(),
        "Routes.kt": patch_routes(),
    }

    print("\n" + "=" * 60)
    for name, ok in results.items():
        print(f"  {'✅' if ok else '❌'} {name}")

    if all(results.values()):
        print("\n✅ Все патчи применены.")
        print("\nСборка: ./gradlew assembleDebug")
        print("\nПосле сборки проверь:")
        print("  1. Открыть требование → под статусом секция «Материалы и задания»")
        print("  2. Нажать «Ответить» на задании → bottom sheet с формой")
        print("  3. TopAppBar → иконка «Редактировать» → RequirementEditorScreen")
    else:
        print("\n⚠️  Часть патчей не удалась. Смотри .bak.")

    print("=" * 60)


if __name__ == "__main__":
    main()