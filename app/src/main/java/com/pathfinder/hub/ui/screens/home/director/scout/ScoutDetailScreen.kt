package com.pathfinder.hub.ui.screens.home.director.scout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.common.AttachmentPreview
import com.pathfinder.hub.domain.permissions.PermissionChecker
import com.pathfinder.hub.ui.screens.home.director.ClubContextViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutDetailScreen(
    onBack: () -> Unit,
    viewModel: ScoutDetailViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    val canAward = PermissionChecker.hasPerm(clubState.currentUser, "learning.approve")
    val completedHonorIds = state.detail?.honorsCompleted?.map { it.honorId }?.toSet() ?: emptySet()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.detail?.user?.let { "${it.firstName} ${it.lastName}".trim() }
                            ?: "Прогресс следопыта"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (canAward) {
                        IconButton(onClick = viewModel::openAwardDialog) {
                            Icon(Icons.Default.Add, "Выдать специализацию")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.detail == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(state.errorMessage ?: "Нет данных", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                val d = state.detail!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (d.completedLevels.isNotEmpty()) {
                        item { Text("Пройденные ступени", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        items(d.completedLevels, key = { "done_${it.levelId}" }) { lvl ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.size(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(lvl.levelName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    lvl.completedAt?.let {
                                        Text("Завершено: ${fmt.format(it)}", style = MaterialTheme.typography.bodySmall, color = PathfinderBlue)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.size(8.dp)) }
                    }

                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(d.currentLevelName ?: "Все ступени пройдены", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("Специализации: в работе ${d.honorsInProgress.size}, завершено ${d.honorsCompleted.size}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    item { Text("Текущая ступень", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    if (d.levelRequirements.isEmpty()) {
                        item { Text("Нет требований", style = MaterialTheme.typography.bodySmall) }
                    } else {
                        items(d.levelRequirements, key = { "lvl_${it.id}" }) { req ->
                            RequirementRow(
                                req = req,
                                fmt = fmt,
                                refreshUrl = { u -> viewModel.refreshAttachmentUrl(u) },
                            )
                        }
                    }

                    if (d.honorsInProgress.isNotEmpty()) {
                        item { Spacer(Modifier.size(8.dp)); Text("Специализации в работе", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        d.honorsInProgress.forEach { h ->
                            item {
                                HonorSection(
                                    h = h,
                                    fmt = fmt,
                                    refreshUrl = { u -> viewModel.refreshAttachmentUrl(u) },
                                )
                            }
                        }
                    }

                    if (d.honorsCompleted.isNotEmpty()) {
                        item { Spacer(Modifier.size(8.dp)); Text("Завершённые специализации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        items(d.honorsCompleted, key = { "honor_done_${it.honorId}" }) { h ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = PathfinderBlue, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.size(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.honorName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    h.completedAt?.let {
                                        Text("Завершено: ${fmt.format(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showAwardDialog) {
        ScoutAwardDialog(
            allHonors = state.allHonors,
            alreadyCompletedIds = completedHonorIds,
            onDismiss = viewModel::dismissAwardDialog,
            onConfirm = { honorId ->
                viewModel.awardSpecialization(honorId)
            }
        )
    }
}

@Composable
private fun RequirementRow(
    req: RequirementStatus,
    fmt: SimpleDateFormat,
    refreshUrl: (suspend (String) -> String?)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(if (req.approved) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
            tint = if (req.approved) PathfinderBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(req.text, style = MaterialTheme.typography.bodyMedium)
            if (req.approved && req.approvedAt != null) {
                Text("Выполнено: ${fmt.format(req.approvedAt)}", style = MaterialTheme.typography.bodySmall, color = PathfinderBlue)
            } else if (req.submitted) {
                Text("На проверке", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Отчёты (файлы, загруженные подростком)
            if (req.mediaUrls.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                req.mediaUrls.forEach { url ->
                    AttachmentPreview(url = url, refreshUrl = refreshUrl)
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
private fun HonorSection(
    h: HonorProgressDetail,
    fmt: SimpleDateFormat,
    refreshUrl: (suspend (String) -> String?)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = PathfinderBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.honorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${h.requirementsApproved}/${h.requirementsTotal} · ${h.percent}%",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(8.dp))
            h.requirements.forEach { req ->
                RequirementRow(req = req, fmt = fmt, refreshUrl = refreshUrl)
            }
        }
    }
}
