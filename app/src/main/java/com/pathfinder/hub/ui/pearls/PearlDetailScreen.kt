package com.pathfinder.hub.ui.pearls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PearlDetailScreen(
    onBack: () -> Unit,
    onOpenInBible: (bookId: String, chapter: Int, verseId: String) -> Unit,
    onTrain: () -> Unit = {},
    viewModel: PearlDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pearl = state.pearl

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Жемчужина") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading || pearl == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Неделя ${pearl.weekNumber} · ${pearl.seasonTitle}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pearl.reference,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (!pearl.text.isNullOrBlank()) {
                Text(pearl.text, style = MaterialTheme.typography.bodyLarge)
            } else if (pearl.isExam) {
                Text(
                    "Экзамен по сезону «${pearl.seasonTitle}». Повтори все стихи сезона.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!pearl.isExam) {
                Button(
                    onClick = onTrain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Тренировать")
                }
            }

            val memorized = state.progress?.memorized == true
            Button(
                onClick = { viewModel.toggleMemorized() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (memorized) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(if (memorized) "Заучено" else "Отметить как заученное")
            }

            if (!pearl.isExam && pearl.verseId != null) {
                OutlinedButton(
                    onClick = {
                        val parts = pearl.verseId.split(":")
                        if (parts.size >= 4) {
                            val bookId = "${parts[0]}:${parts[1]}"
                            val chapter = parts[2].toIntOrNull() ?: 1
                            onOpenInBible(bookId, chapter, pearl.verseId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Открыть в Библии")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
