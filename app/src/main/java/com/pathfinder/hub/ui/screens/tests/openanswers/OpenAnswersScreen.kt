package com.pathfinder.hub.ui.screens.tests.openanswers

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenAnswersScreen(
    onBack: () -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    viewModel: OpenAnswersViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Открытые ответы") },
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
        OpenAnswersContent(
            onOpenAttempt = onOpenAttempt,
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
        )
    }
}

@Composable
fun OpenAnswersContent(
    onOpenAttempt: (attemptId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OpenAnswersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp), tint = PathfinderGreen)
                    Spacer(Modifier.height(8.dp))
                    Text("Все ответы проверены", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Когда следопыт сдаст тест с открытым ответом — он появится здесь",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
                val grouped = remember(state.rows) { state.rows.groupBy { it.attempt.userId to it.teenName } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (key, groupRows) ->
                        val (_, teenName) = key
                        val groupKey = "g_${key.first}"
                        val defaultExpanded = groupRows.size <= 3
                        val isExpanded = expandedGroups[groupKey] ?: defaultExpanded
                        item(key = groupKey) {
                            GroupHeader(
                                title = teenName.ifBlank { key.first.take(8) },
                                count = groupRows.sumOf { it.unreviewedCount },
                                expanded = isExpanded,
                                onToggle = { expandedGroups[groupKey] = !isExpanded },
                            )
                        }
                        if (isExpanded) {
                            items(groupRows, key = { it.attempt.id }) { row ->
                                OpenAnswerCard(row, onClick = { onOpenAttempt(row.attempt.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = PathfinderBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AssistChip(onClick = {}, label = { Text("$count") })
            Spacer(Modifier.width(4.dp))
            Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OpenAnswerCard(row: OpenAnswerRow, onClick: () -> Unit) {
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.teenName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(row.testTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text("${row.unreviewedCount}") })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Сдан: " + fmt.format(row.attempt.completedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
