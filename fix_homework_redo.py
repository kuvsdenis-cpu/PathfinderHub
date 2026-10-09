#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Homework redo (P) - 09.10.2026
- Teen при rejected может нажать "Переделать" → статус assigned.
Перезаписывает HomeworkTab.kt (сохраняет Share из fix_share_extra).
Запуск:
    python fix_homework_redo.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")
PATH = "app/src/main/java/com/pathfinder/hub/ui/screens/homework/HomeworkTab.kt"

CONTENT = '''package com.pathfinder.hub.ui.screens.homework

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.gamification.HomeworkEntity
import com.pathfinder.hub.data.repository.AchievementRepository
import com.pathfinder.hub.ui.share.ShareCardGenerator
import com.pathfinder.hub.ui.share.ShareCardHelper
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * Переделать после rejected.
     * Сбрасывает статус в assigned, чтобы teen мог снова отправить.
     */
    fun redoHomework(id: String) {
        val item = _state.value.items.find { it.id == id } ?: return
        viewModelScope.launch {
            repo.upsertHomework(
                item.copy(
                    status = "assigned",
                    approvedAt = null,
                    pendingSync = true,
                )
            )
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
                    HomeworkCard(
                        h = h,
                        onDone = { viewModel.markDone(h.id) },
                        onRedo = { viewModel.redoHomework(h.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(
    h: HomeworkEntity,
    onDone: () -> Unit,
    onRedo: () -> Unit,
) {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ShareCardGenerator.generate(
                                context = ctx,
                                emoji = "\\uD83D\\uDCDA",
                                title = h.title,
                                subtitle = "Срок: " + fmt.format(h.dueDate),
                                userName = null,
                            )
                        }
                        ShareCardHelper.share(ctx, file, "Домашка в Pathfinder Hub: " + h.title)
                    }
                }) {
                    Icon(Icons.Default.Share, "Поделиться", tint = PathfinderBlue)
                }
            }

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
                "rejected" -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Возвращено на доработку",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onRedo,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Refresh, null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Переделать")
                            }
                        }
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

p = ROOT / PATH
if not p.exists():
    print("[ERROR] не найден: " + PATH); sys.exit(1)

bak = p.with_suffix(p.suffix + ".bak." + STAMP)
shutil.copy2(p, bak)
print("[BAK]  " + bak.name)

p.write_text(CONTENT, encoding="utf-8", newline="\n")
print("[REWRITE] " + PATH)
print()
print("Готово. Дальше:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print()
print("Проверка:")
print("  1. Директор → Проверка → Вернуть домашку")
print("  2. Teen → Мои задачи → Домашка")
print("  3. Карточка красная 'Возвращено на доработку' + кнопка 'Переделать'")
print("  4. Нажать → статус 'assigned' → снова 'Отметить выполненным'")