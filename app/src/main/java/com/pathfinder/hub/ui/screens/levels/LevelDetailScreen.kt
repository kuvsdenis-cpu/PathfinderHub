package com.pathfinder.hub.ui.screens.levels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGray
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelDetailScreen(
    onBack: () -> Unit,
    onOpenRequirement: (levelId: String, requirementId: String) -> Unit,
    viewModel: LevelDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.level?.name ?: "Ступень") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.errorMessage!!, color = PathfinderRed)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Шапка с прогрессом
                    item {
                        ProgressHeader(
                            approved = state.approvedCount,
                            total = state.totalCount,
                            percent = state.progressPercent
                        )
                    }

                    // Разделы
                    items(state.sections, key = { it.section.id }) { section ->
                        SectionCard(
                            section = section,
                            onToggle = { viewModel.toggleSection(section.section.id) },
                            onRequirementClick = { reqId ->
                                onOpenRequirement(state.level?.id ?: "", reqId)
                            }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(approved: Int, total: Int, percent: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PathfinderBlue.copy(alpha = 0.1f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Прогресс ступени",
                style = MaterialTheme.typography.labelLarge,
                color = PathfinderBlue
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$approved из $total требований",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PathfinderBlue
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = PathfinderBlue
            )
        }
    }
}

@Composable
private fun SectionCard(
    section: SectionUiModel,
    onToggle: () -> Unit,
    onRequirementClick: (String) -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
        Column {
            // Заголовок раздела (кликабельный)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = section.section.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${section.approvedCount} из ${section.totalCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PathfinderGray
                    )
                }
                Icon(
                    imageVector = if (section.isExpanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = PathfinderBlue
                )
            }

            // Требования (раскрывающиеся)
            AnimatedVisibility(visible = section.isExpanded) {
                Column {
                    section.requirements.forEach { req ->
                        RequirementRow(
                            model = req,
                            onClick = { onRequirementClick(req.requirement.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(model: RequirementUiModel, onClick: () -> Unit) {
    val isApproved = model.status == "approved"
    val isSubmitted = model.status == "submitted"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isApproved) Icons.Default.CheckCircle
            else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = when {
                isApproved -> PathfinderGreen
                isSubmitted -> PathfinderBlue
                else -> PathfinderGray
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = model.requirement.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (isApproved) TextDecoration.LineThrough else null,
                color = if (isApproved) PathfinderGray else Color.Unspecified
            )
            if (isSubmitted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "На проверке",
                    style = MaterialTheme.typography.labelLarge,
                    color = PathfinderBlue
                )
            } else if (model.status == "rejected") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Отклонено: ${model.comment ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = PathfinderRed
                )
            }
        }
    }
}