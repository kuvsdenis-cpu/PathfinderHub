package com.pathfinder.hub.ui.screens.tests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGray
import com.pathfinder.hub.ui.theme.PathfinderGreen

/**
 * Универсальная секция «Тесты» для любого экрана.
 *
 * Пустая — не рендерится.
 *
 * @param scope  "honor" | "level" | "homework" | "quest" | "event" | "standalone"
 * @param scopeId  id объекта, к которому привязан тест (null для standalone)
 * @param onOpenTest  переход на экран прохождения теста по testId
 */
@Composable
fun TestsForScopeSection(
    scope: String,
    scopeId: String?,
    onOpenTest: (testId: String) -> Unit,
    viewModel: TestsForScopeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(scope, scopeId) {
        viewModel.load(scope, scopeId)
    }

    if (state.isLoading || state.rows.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Тесты",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        state.rows.forEach { row ->
            TestCard(row = row, onClick = { onOpenTest(row.test.id) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TestCard(row: TestRow, onClick: () -> Unit) {
    val passed = row.passed
    val pending = row.pendingReview
    val bgColor = when {
        passed -> PathfinderGreen.copy(alpha = 0.10f)
        pending -> PathfinderBlue.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle
                else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (passed) PathfinderGreen else PathfinderGray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.test.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (row.test.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        row.test.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                val status = when {
                    passed -> "Пройден"
                    pending -> "На проверке"
                    else -> "Не пройден · порог ${row.test.passScore}%"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (passed) PathfinderGreen else PathfinderBlue
                )
            }
        }
    }
}
