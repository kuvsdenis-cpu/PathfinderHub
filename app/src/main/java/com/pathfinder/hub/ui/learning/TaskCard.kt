package com.pathfinder.hub.ui.learning

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
