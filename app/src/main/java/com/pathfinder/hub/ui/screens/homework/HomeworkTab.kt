package com.pathfinder.hub.ui.screens.homework

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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val activeTab: Int = 0,
    val items: List<HomeworkEntity> = emptyList(),
    val archived: List<HomeworkEntity> = emptyList(),
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
                launch {
                    repo.observeActiveHomework(uid).collect { list ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                items = list.sortedByDescending { h -> h.dueDate }
                            )
                        }
                    }
                }
                launch {
                    repo.observeArchivedHomework(uid).collect { list ->
                        _state.update { it.copy(archived = list) }
                    }
                }
            }
        }
    }

    fun setTab(idx: Int) = _state.update { it.copy(activeTab = idx) }

    fun archiveHomework(id: String) {
        viewModelScope.launch { repo.archiveHomework(id) }
    }

    fun restoreHomework(id: String) {
        viewModelScope.launch { repo.restoreHomework(id) }
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
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.activeTab) {
            Tab(
                selected = state.activeTab == 0,
                onClick = { viewModel.setTab(0) },
                text = { Text("Активные (${state.items.size})") }
            )
            Tab(
                selected = state.activeTab == 1,
                onClick = { viewModel.setTab(1) },
                text = { Text("Архив (${state.archived.size})") }
            )
        }
        val current = if (state.activeTab == 0) state.items else state.archived
        if (current.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(
                    if (state.activeTab == 0)
                        "Домашки пока нет"
                    else
                        "Архив пуст"
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(current, key = { it.id }) { h ->
                    if (state.activeTab == 1) {
                        ArchivedHomeworkCard(h, onRestore = { viewModel.restoreHomework(h.id) })
                    } else {
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
}

@Composable
private fun ArchivedHomeworkCard(
    h: HomeworkEntity,
    onRestore: () -> Unit,
) {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                h.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (h.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    h.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (h.archivedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "В архиве с: ${fmt.format(h.archivedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Восстановить")
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
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
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
                                emoji = "\uD83D\uDCDA",
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
            if (!h.rewardText.isNullOrBlank()) {
                Text(
                    "Приз: ${h.rewardText}",
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

            // Кнопка «В архив» убрана из teen-UI:
            // архивировать может только директор / co-director с правами.
        }
    }
}
