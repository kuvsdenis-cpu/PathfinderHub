package com.pathfinder.hub.ui.readingplan

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingPlanDetailScreen(
    onBack: () -> Unit,
    onOpenDay: (String) -> Unit,
    viewModel: ReadingPlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fmt = SimpleDateFormat("d MMM", Locale.forLanguageTag("ru"))
    val listState = rememberLazyListState()

    // Автоскролл к текущему дню, когда оба готовы: список дней + currentDay.
    // Ключи — state.days.size и state.currentDay?.id: сработает один раз,
    // когда данные загрузились. Индекс: +1 за карточку «сегодня» сверху,
    // +1 за прогресс-бар, ещё +1 за Spacer(8.dp) — итого +3.
    LaunchedEffect(state.days.size, state.currentDay?.id) {
        val current = state.currentDay ?: return@LaunchedEffect
        if (state.days.isEmpty()) return@LaunchedEffect
        val idx = state.days.indexOfFirst { it.id == current.id }
        if (idx >= 0) {
            // 3 header-элемента перед первым днём: «сегодня», прогресс, Spacer
            listState.animateScrollToItem(idx + 3)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.schedule?.title ?: "План чтения") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.currentDay?.let { cur ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDay(cur.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Сегодня · День ${cur.dayNumber}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                cur.reference,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (state.progress[cur.id] != null) {
                                Spacer(Modifier.height(4.dp))
                                Text("Прочитано", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                val total = state.days.size
                val done = state.progress.size
                val fraction = if (total > 0) done.toFloat() / total else 0f
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Прогресс: $done / $total",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            items(state.days, key = { it.id }) { d ->
                val done = state.progress[d.id] != null
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDay(d.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "День ${d.dayNumber} · ${fmt.format(d.date)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                d.reference,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            imageVector = if (done) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (done) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
