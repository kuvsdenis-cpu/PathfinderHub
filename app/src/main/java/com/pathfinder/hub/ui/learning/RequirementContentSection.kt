package com.pathfinder.hub.ui.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
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
    viewModel: RequirementContentViewModel,
    onCompletionChanged: (hasRequired: Boolean, allDone: Boolean) -> Unit = { _, _ -> },
    onAttachFile: (taskId: String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
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
                    onOpenLink = { url -> FileIntentUtil.openUrl(context, url) },
                    onOpenFile = { url -> FileIntentUtil.openFile(context, url) }
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
            onAttachFile = { onAttachFile(t.id) }
        )
    }
}
