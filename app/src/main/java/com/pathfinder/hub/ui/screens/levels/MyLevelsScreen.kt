package com.pathfinder.hub.ui.screens.levels

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGray
import com.pathfinder.hub.ui.theme.PathfinderGreen

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLevelsScreen(
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    viewModel: MyLevelsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои ступени") },
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.levels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ступени ещё не загружены.\n" +
                            "Проверьте, что JSON-файлы в assets загрузились в Room.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PathfinderGray
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.levels, key = { it.level.id }) { item ->
                LevelCard(
                    item = item,
                    onClick = {
                        if (!item.isLocked) {
                            onOpenLevel(item.level.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LevelCard(
    item: LevelUiModel,
    onClick: () -> Unit
) {
    val containerColor = when {
        item.isCompleted -> PathfinderGreen.copy(alpha = 0.15f)
        item.isCurrent -> PathfinderBlue.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isLocked, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Индикатор статуса
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            item.isCompleted -> PathfinderGreen
                            item.isCurrent -> PathfinderBlue
                            else -> PathfinderGray.copy(alpha = 0.3f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.isCompleted -> Icons.Default.CheckCircle
                        item.isLocked -> Icons.Default.Lock
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.level.order}. ${item.level.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ТЕКУЩАЯ",
                            style = MaterialTheme.typography.labelLarge,
                            color = PathfinderBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (item.isCompleted) {
                        "Ступень пройдена"
                    } else if (item.isLocked) {
                        "Откроется после предыдущей ступени"
                    } else {
                        "Сдано: ${item.approvedCount} из ${item.totalCount}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = PathfinderGray
                )

                if (!item.isCompleted && !item.isLocked && item.totalCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { item.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = PathfinderBlue
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${item.progressPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = PathfinderBlue
                    )
                }
            }
        }
    }
}