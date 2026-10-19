package com.pathfinder.hub.ui.activity

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моя активность") },
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- Общий уровень ----------
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = PathfinderBlue.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Bolt, null,
                            tint = PathfinderBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Общий уровень активности",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Уровень ${state.overallLevel} из 5",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = PathfinderBlue
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.overallPercent },
                        modifier = Modifier.fillMaxWidth(),
                        color = PathfinderBlue
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Среднее от трёх шкал ниже",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Требования ----------
            ScaleCard(
                icon = Icons.Default.CheckCircle,
                scale = state.requirements,
                color = PathfinderBlue,
            )

            // ---------- Сообщения ----------
            ScaleCard(
                icon = Icons.Default.Chat,
                scale = state.messages,
                color = PathfinderGreen,
            )

            // ---------- Посещения ----------
            ScaleCard(
                icon = Icons.Default.QrCodeScanner,
                scale = state.visits,
                color = PathfinderYellow,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScaleCard(
    icon: ImageVector,
    scale: ActivityScale,
    color: androidx.compose.ui.graphics.Color,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    scale.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Ур. ${scale.level}/5",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (scale.level - 1) / 4f },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Сейчас: ${scale.count}")
                    scale.nextThreshold?.let {
                        append(" · До следующего уровня: ${it - scale.count}")
                    } ?: append(" · Максимум")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
