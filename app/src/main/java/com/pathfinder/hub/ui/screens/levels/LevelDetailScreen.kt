package com.pathfinder.hub.ui.screens.levels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelDetailScreen(
    onBack: () -> Unit,
    onOpenRequirement: (String, String) -> Unit, // levelId, requirementId
    viewModel: LevelDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.level?.name ?: "Ступень") },
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
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            }
            state.sections.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Требования не найдены", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Проверьте, что контент загружен в БД", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Прогресс ступени
                    item {
                        ProgressHeader(
                            approved = state.approvedCount,
                            total = state.totalCount
                        )
                    }

                    // Разделы и требования
                    items(state.sections) { section ->
                        SectionCard(
                            section = section,
                            onActionClick = { req ->
                                if (req.type == "practice" || req.type == "report") {
                                    onOpenRequirement(state.level?.id ?: "", req.id)
                                } else {
                                    viewModel.onRequirementAction(req.id, req.type)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(approved: Int, total: Int) {
    val percent = if (total > 0) (approved * 100 / total) else 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Прогресс ступени", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = PathfinderBlue
            )
            Spacer(Modifier.height(8.dp))
            Text("Выполнено: $approved из $total требований ($percent%)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionCard(
    section: SectionUiModel,
    onActionClick: (RequirementUiModel) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(section.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PathfinderBlue)
            Spacer(Modifier.height(12.dp))

            section.requirements.forEach { req ->
                RequirementRow(req, onActionClick)
                if (req != section.requirements.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(req: RequirementUiModel, onActionClick: (RequirementUiModel) -> Unit) {
    // ИСПРАВЛЕНИЕ: Разделяем определение иконки и цвета, чтобы избежать неоднозначности типов
    val icon = when (req.status) {
        "approved" -> Icons.Default.CheckCircle
        "submitted", "in_progress" -> Icons.Default.Pending
        "rejected" -> Icons.Default.Error
        else -> Icons.Default.CheckCircle
    }

    val color = when (req.status) {
        "approved" -> PathfinderGreen
        "submitted", "in_progress" -> Color(0xFFFFA000)
        "rejected" -> PathfinderRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(req.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Тип: ${getTypeName(req.type)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Кнопка действия
        if (req.status == "not_started" || req.status == "rejected") {
            OutlinedButton(
                onClick = { onActionClick(req) },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                if (req.type == "practice" || req.type == "report") {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text("Выполнить")
            }
        } else if (req.status == "submitted") {
            Text(
                "На проверке",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFA000),
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        } else if (req.status == "approved") {
            Text(
                "Сдано",
                style = MaterialTheme.typography.labelMedium,
                color = PathfinderGreen,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

private fun getTypeName(type: String): String = when (type) {
    "boolean" -> "Отметка"
    "text" -> "Текстовый ответ"
    "practice" -> "Практика / Отчет"
    "test" -> "Тест"
    else -> type
}