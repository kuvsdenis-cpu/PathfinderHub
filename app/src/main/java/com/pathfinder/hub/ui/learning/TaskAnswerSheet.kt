package com.pathfinder.hub.ui.learning

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
