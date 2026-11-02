package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Лог действий клуба (append-only).
 * Виден только директору / co-director с settings.edit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AuditContent(
    modifier: Modifier = Modifier,
    viewModel: AuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.forLanguageTag("ru")) }
    var filtersExpanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        // ---------- Кнопка «Фильтры» (по умолчанию свёрнуто) ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val activeCount =
                (if (state.filterType != null) 1 else 0) +
                (if (state.filterAction != null) 1 else 0) +
                (if (state.filterUser.isNotBlank()) 1 else 0)
            Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                buildString {
                    append("Фильтры")
                    if (activeCount > 0) append(" · $activeCount")
                    append(" · Всего: ${state.entries.size}")
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (activeCount > 0) {
                TextButton(onClick = viewModel::resetFilters) { Text("Сбросить") }
            }
            TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                Text(if (filtersExpanded) "Скрыть" else "Раскрыть")
            }
        }

        if (filtersExpanded) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Тип сущности", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.filterType == null,
                        onClick = { viewModel.setFilterType(null) },
                        label = { Text("Все") },
                    )
                    TYPE_OPTIONS.forEach { (key, label) ->
                        FilterChip(
                            selected = state.filterType == key,
                            onClick = { viewModel.setFilterType(key) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Действие", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.filterAction == null,
                        onClick = { viewModel.setFilterAction(null) },
                        label = { Text("Все") },
                    )
                    ACTION_OPTIONS.forEach { (key, label) ->
                        FilterChip(
                            selected = state.filterAction == key,
                            onClick = { viewModel.setFilterAction(key) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.filterUser,
                    onValueChange = viewModel::setFilterUser,
                    label = { Text("Пользователь (ID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.filtered.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.entries.isEmpty()) "Записей пока нет"
                        else "Нет записей по фильтрам",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.filtered, key = { it.id }) { entry ->
                    AuditCard(entry = entry, fmt = fmt)
                }
            }
        }
    }
}

@Composable
private fun AuditCard(entry: AuditLogEntity, fmt: SimpleDateFormat) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    actionLabel(entry.action),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(typeLabel(entry.targetType)) },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${entry.userRole} · ${entry.userId.take(8)} · ${fmt.format(entry.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!entry.reason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Причина: ${entry.reason}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val nv = entry.newValue
            if (!nv.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                SummaryOrDetails(nv)
            }
        }
    }
}


@Composable
private fun SummaryOrDetails(json: String) {
    var expanded by remember { mutableStateOf(false) }
    val summary = remember(json) { summarizeJson(json) }
    Column {
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                if (expanded) json else json.take(80) + "…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                if (expanded) "Скрыть детали" else "Детали",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (expanded) {
            Text(
                prettyPrintJson(json),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun summarizeJson(raw: String): String? = try {
    val o = org.json.JSONObject(raw)
    val parts = buildList {
        listOf("title", "name", "action", "status", "level", "type").forEach { k ->
            if (o.has(k) && !o.isNull(k)) add("$k: ${o.getString(k)}")
        }
    }
    if (parts.isEmpty()) null else parts.joinToString(" · ")
} catch (_: Exception) { null }

private fun prettyPrintJson(raw: String): String = try {
    org.json.JSONObject(raw).toString(2)
} catch (_: Exception) { raw }

private val TYPE_OPTIONS: List<Pair<String, String>> = listOf(
    "user" to "Пользователь",
    "event" to "Событие",
    "comment" to "Комментарий",
    "task" to "Задача",
    "progress" to "Прогресс",
    "report" to "Отчёт",
    "settings" to "Настройки",
)

private val ACTION_OPTIONS: List<Pair<String, String>> = listOf(
    "create" to "Создано",
    "update" to "Обновлено",
    "delete" to "Удалено",
    "approve" to "Одобрено",
    "reject" to "Отклонено",
    "hide" to "Скрыто",
    "restore" to "Восстановлено",
    "role_change" to "Смена роли",
)

private fun typeLabel(type: String): String =
    TYPE_OPTIONS.firstOrNull { it.first == type }?.second ?: type

private fun actionLabel(action: String): String =
    ACTION_OPTIONS.firstOrNull { it.first == action }?.second ?: action
