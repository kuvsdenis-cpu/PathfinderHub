#!/usr/bin/env python3
"""
Генератор Compose-экранов для приложения Pathfinder Hub.
Создаёт все экраны для следопыта: специализации, события, задачи, профиль.
Плюс обновляет Routes, PathfinderNavHost, репозитории, DAO.
"""

import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"

FILES = []

def add(path, content):
    FILES.append((path, content.strip() + "\n"))

def write_all():
    for path, content in FILES:
        full = ROOT / path
        full.parent.mkdir(parents=True, exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"✓ {path}")
    print(f"\n✅ Создано файлов: {len(FILES)}")

# ============================================================
# БЛОК A: СПЕЦИАЛИЗАЦИИ
# ============================================================

# ... (все файлы ViewModel/Screen из Блока A, что я давал выше)
# Ниже — только НОВЫЕ файлы, которых ещё не было в проекте.

# 5. HonorRequirementDetailViewModel.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/HonorRequirementDetailViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class HonorRequirementDetailUiState(
    val isLoading: Boolean = true,
    val requirement: HonorRequirementEntity? = null,
    val status: String = "not_started",
    val comment: String? = null,
    val honorName: String = "",
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HonorRequirementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(HonorRequirementDetailUiState())
    val state: StateFlow<HonorRequirementDetailUiState> = _state.asStateFlow()

    init { loadRequirement() }

    private fun loadRequirement() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
            return
        }
        viewModelScope.launch {
            val honor = honorRepository.getHonor(honorId)
            val reqs = honorRepository.getRequirements(honorId)
            val req = reqs.firstOrNull { it.id == requirementId }
            if (req == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Требование не найдено") }
                return@launch
            }
            val progressList = honorRepository.getProgressSnapshot(userId, honorId)
            val p = progressList.firstOrNull { it.requirementId == requirementId }
            _state.update {
                it.copy(
                    isLoading = false,
                    requirement = req,
                    status = p?.status ?: "not_started",
                    comment = p?.comment,
                    honorName = honor?.name ?: ""
                )
            }
        }
    }

    fun submitTheory() {
        val userId = sessionManager.getUserId() ?: return
        _state.update { it.copy(isSaving = true, successMessage = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                honorRepository.upsertProgress(
                    HonorProgressEntity(
                        userId = userId, honorId = honorId, requirementId = requirementId,
                        status = "submitted", submittedAt = Date(),
                        approvedBy = null, approvedAt = null,
                        mediaUrls = emptyList(), comment = null, pendingSync = true
                    )
                )
                _state.update {
                    it.copy(isSaving = false, status = "submitted",
                        successMessage = "Отправлено на проверку")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
''')

# 6. HonorRequirementDetailScreen.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/HonorRequirementDetailScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HonorRequirementDetailScreen(
    onBack: () -> Unit,
    onOpenTest: () -> Unit,
    onOpenReport: () -> Unit,
    viewModel: HonorRequirementDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Требование") },
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val req = state.requirement ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Text(state.honorName, style = MaterialTheme.typography.labelLarge,
                color = PathfinderBlue)
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(req.text, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Тип: ${req.type}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("Статус: ${statusLabel(state.status)}",
                style = MaterialTheme.typography.bodyLarge,
                color = PathfinderBlue)

            Spacer(Modifier.weight(1f))

            if (req.type == "test") {
                Button(onClick = onOpenTest,
                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Пройти тест")
                }
            } else {
                Button(onClick = onOpenReport,
                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Загрузить отчёт")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.status == "not_started" || state.status == "rejected") {
                OutlinedButton(onClick = { viewModel.submitTheory() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Отметить как выполненное")
                }
            }
        }
    }
}

private fun statusLabel(s: String) = when (s) {
    "not_started" -> "Не начато"
    "submitted" -> "На проверке"
    "approved" -> "Сдано"
    "rejected" -> "Отклонено"
    else -> s
}
''')

# 7. TestViewModel.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/TestViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.domain.usecase.learning.SubmitTestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestUiState(
    val isLoading: Boolean = true,
    val questions: List<TestQuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val isFinished: Boolean = false,
    val passed: Boolean = false,
    val score: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val submitTest: SubmitTestUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""
    private val _state = MutableStateFlow(TestUiState())
    val state: StateFlow<TestUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val qs = honorRepository.getRandomQuestions(honorId, 10)
            _state.update { it.copy(isLoading = false, questions = qs) }
        }
    }

    fun selectAnswer(questionId: String, answer: String) {
        _state.update { it.copy(answers = it.answers + (questionId to answer)) }
    }

    fun next() {
        val s = _state.value
        if (s.currentIndex < s.questions.lastIndex) {
            _state.update { it.copy(currentIndex = s.currentIndex + 1) }
        } else {
            finish()
        }
    }

    fun previous() {
        val s = _state.value
        if (s.currentIndex > 0) {
            _state.update { it.copy(currentIndex = s.currentIndex - 1) }
        }
    }

    private fun finish() {
        val s = _state.value
        val userId = sessionManager.getUserId() ?: return
        val correct = s.questions.count { q ->
            s.answers[q.id]?.equals(q.correctAnswer, ignoreCase = true) == true
        }
        viewModelScope.launch {
            val result = submitTest(userId, honorId, correct, s.questions.size)
            result.fold(
                onSuccess = { passed ->
                    val score = if (s.questions.isNotEmpty()) correct * 100 / s.questions.size else 0
                    _state.update { it.copy(isFinished = true, passed = passed, score = score) }
                },
                onFailure = { e ->
                    _state.update { it.copy(errorMessage = e.message) }
                }
            )
        }
    }
}
''')

# 8. TestScreen.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/TestScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: TestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тест") },
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
            state.isFinished -> ResultScreen(state, onFinished)
            state.questions.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center
            ) { Text("Вопросы для этой специализации ещё не добавлены") }
            else -> QuestionView(state, viewModel)
        }
    }
}

@Composable
private fun ResultScreen(state: TestUiState, onFinished: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.passed) "Тест пройден!" else "Тест не пройден",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (state.passed) PathfinderGreen else PathfinderRed
        )
        Spacer(Modifier.height(16.dp))
        Text("Результат: ${state.score}%",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Проходной балл: 90%",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Вернуться")
        }
    }
}

@Composable
private fun QuestionView(state: TestUiState, viewModel: TestViewModel) {
    val q = state.questions[state.currentIndex]
    val selected = state.answers[q.id]

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        LinearProgressIndicator(
            progress = { (state.currentIndex + 1).toFloat() / state.questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Вопрос ${state.currentIndex + 1} из ${state.questions.size}",
            style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)
        Spacer(Modifier.height(16.dp))
        Text(q.text, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        q.options.forEach { option ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable { viewModel.selectAnswer(q.id, option) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == option)
                        PathfinderBlue.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selected == option,
                        onClick = { viewModel.selectAnswer(q.id, option) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.currentIndex > 0) {
                OutlinedButton(
                    onClick = viewModel::previous,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Назад") }
            }
            Button(
                onClick = viewModel::next,
                enabled = selected != null,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text(if (state.currentIndex < state.questions.lastIndex) "Далее" else "Завершить")
            }
        }
    }
}
''')

# 9. ReportUploadViewModel.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/ReportUploadViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorProgressEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ReportUploadUiState(
    val comment: String = "",
    val mediaUrls: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportUploadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val honorId: String = savedStateHandle.get<String>("honorId") ?: ""
    private val requirementId: String = savedStateHandle.get<String>("requirementId") ?: ""

    private val _state = MutableStateFlow(ReportUploadUiState())
    val state: StateFlow<ReportUploadUiState> = _state.asStateFlow()

    fun onCommentChange(v: String) = _state.update { it.copy(comment = v) }

    fun addMediaUrl(url: String) {
        _state.update { it.copy(mediaUrls = it.mediaUrls + url) }
    }

    fun removeMediaUrl(url: String) {
        _state.update { it.copy(mediaUrls = it.mediaUrls - url) }
    }

    fun submit() {
        val userId = sessionManager.getUserId() ?: return
        _state.update { it.copy(isSaving = true, successMessage = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                honorRepository.upsertProgress(
                    HonorProgressEntity(
                        userId = userId, honorId = honorId, requirementId = requirementId,
                        status = "submitted", submittedAt = Date(),
                        approvedBy = null, approvedAt = null,
                        mediaUrls = _state.value.mediaUrls,
                        comment = _state.value.comment.ifBlank { null },
                        pendingSync = true
                    )
                )
                _state.update {
                    it.copy(isSaving = false, successMessage = "Отчёт отправлен на проверку")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
''')

# 10. ReportUploadScreen.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/ReportUploadScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportUploadScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ReportUploadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessages()
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отчёт") },
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::onCommentChange,
                label = { Text("Комментарий к отчёту") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Медиа-файлы", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Загрузка медиа будет доступна после подключения Firebase Storage.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            state.mediaUrls.forEach { url ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(url, Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeMediaUrl(url) }) {
                        Icon(Icons.Default.Close, "Удалить")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isSaving) CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                ) else Text("Отправить на проверку")
            }
        }
    }
}
''')

# 11. MyHonorsViewModel.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/MyHonorsViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyHonorsUiState(
    val isLoading: Boolean = true,
    val inProgress: List<HonorEntity> = emptyList(),
    val completed: List<HonorEntity> = emptyList(),
    val available: List<HonorEntity> = emptyList()
)

@HiltViewModel
class MyHonorsViewModel @Inject constructor(
    private val honorRepository: HonorRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(MyHonorsUiState())
    val state: StateFlow<MyHonorsUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            combine(
                honorRepository.observeAllHonors(),
                honorRepository.observeCompletions(userId)
            ) { all, completions ->
                val completedIds = completions.map { it.honorId }.toSet()
                val inProgress = all.filter { it.id !in completedIds }
                val completed = all.filter { it.id in completedIds }
                Triple(inProgress, completed, all.filter { it.id !in completedIds && it.isLocal.not() })
            }.collect { (inProg, comp, avail) ->
                _state.update {
                    it.copy(isLoading = false, inProgress = inProg,
                        completed = comp, available = avail)
                }
            }
        }
    }
}
''')

# 12. MyHonorsScreen.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/honors/MyHonorsScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHonorsScreen(
    onBack: () -> Unit,
    onOpenHonor: (String) -> Unit,
    viewModel: MyHonorsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои специализации") },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("В процессе") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Сданные") })
                Tab(selected = tab == 2, onClick = { tab = 2 },
                    text = { Text("Доступные") })
            }

            val list = when (tab) {
                0 -> state.inProgress
                1 -> state.completed
                else -> state.available
            }

            LazyColumn(contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { h ->
                    HonorCard(h) { onOpenHonor(h.id) }
                }
            }
        }
    }
}

@Composable
private fun HonorCard(honor: HonorEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(honor.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Уровень ${honor.level}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
''')

# ============================================================
# БЛОК B: СОБЫТИЯ И ЗАДАЧИ
# ============================================================

# 13. EventsViewModel.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/events/EventsViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val isLoading: Boolean = true,
    val events: List<EventEntity> = emptyList()
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(EventsUiState())
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val user = userRepository.getUser(userId)
            val clubId = user?.clubId ?: ""
            val now = System.currentTimeMillis()
            val end = now + 90L * 24 * 3600 * 1000
            eventRepository.observeEventsInRange(clubId, now, end).collect { list ->
                _state.update { it.copy(isLoading = false, events = list) }
            }
        }
    }
}
''')

# 14. EventsScreen.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/events/EventsScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", Locale("ru")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("События") },
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
        if (state.events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("Пока нет запланированных событий")
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.events, key = { it.id }) { e ->
                EventCard(e, fmt.format(e.dateStart)) { onOpenEvent(e.id) }
            }
        }
    }
}

@Composable
private fun EventCard(event: EventEntity, dateStr: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(dateStr, style = MaterialTheme.typography.bodyLarge,
                color = PathfinderBlue)
            if (event.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(event.location, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
''')

# 15. EventDetailScreen.kt (простой)
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/events/EventDetailScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventDetailUiState(
    val isLoading: Boolean = true,
    val event: EventEntity? = null
)

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""
    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val e = eventRepository.getEvent(eventId)
            _state.update { it.copy(isLoading = false, event = e) }
        }
    }
}
''')

add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/events/EventDetailScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.events

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Событие") },
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
        val e = state.event ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(e.title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("Дата: ${fmt.format(e.dateStart)}",
                style = MaterialTheme.typography.bodyLarge)
            if (e.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Место: ${e.location}",
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (e.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(e.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
''')

# 16. MyTasksViewModel.kt
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/tasks/MyTasksViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyTasksUiState(
    val isLoading: Boolean = true,
    val tasks: List<TaskEntity> = emptyList()
)

@HiltViewModel
class MyTasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(MyTasksUiState())
    val state: StateFlow<MyTasksUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            taskRepository.observeAssignedTasks(userId).collect { list ->
                _state.update { it.copy(isLoading = false, tasks = list) }
            }
        }
    }

    fun acceptTask(id: String) {
        viewModelScope.launch { taskRepository.updateStatus(id, "accepted") }
    }

    fun completeTask(id: String) {
        viewModelScope.launch { taskRepository.updateStatus(id, "done") }
    }
}
''')

add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/tasks/MyTasksScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit,
    viewModel: MyTasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

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
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("Задач пока нет")
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.tasks, key = { it.id }) { t ->
                TaskCard(t, viewModel)
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
            Text("Статус: ${task.status}",
                style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)

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
''')

# ============================================================
# БЛОК C: ПРОФИЛЬ И ЦИФРОВАЯ ФОРМА
# ============================================================

add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.gamification.AchievementProgressEntity
import com.pathfinder.hub.data.local.entity.learning.LevelCompletionEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeenProfileUiState(
    val isLoading: Boolean = true,
    val user: UserEntity? = null,
    val completedLevels: Int = 0,
    val achievements: Int = 0
)

@HiltViewModel
class TeenProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val levelRepository: LevelRepository,
    private val achievementRepository: AchievementRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(TeenProfileUiState())
    val state: StateFlow<TeenProfileUiState> = _state.asStateFlow()

    init {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { u ->
                _state.update { it.copy(user = u) }
            }
        }
        viewModelScope.launch {
            levelRepository.observeCompletions(userId).collect { lc ->
                _state.update { it.copy(completedLevels = lc.size, isLoading = false) }
            }
        }
        viewModelScope.launch {
            achievementRepository.observeEarned(userId).collect { a ->
                _state.update { it.copy(achievements = a.size) }
            }
        }
    }
}
''')

add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/profile/TeenProfileScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeenProfileScreen(
    onBack: () -> Unit,
    viewModel: TeenProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
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
        val u = state.user ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("${u.firstName} ${u.lastName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(u.email, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            StatCard("Пройдено ступеней", state.completedLevels.toString())
            Spacer(Modifier.height(8.dp))
            StatCard("Достижений", state.achievements.toString())
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleLarge,
                color = PathfinderBlue, fontWeight = FontWeight.Bold)
        }
    }
}
''')

# ============================================================
# ЗАПУСК
# ============================================================

if __name__ == "__main__":
    write_all()