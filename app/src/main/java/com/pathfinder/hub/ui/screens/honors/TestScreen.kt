package com.pathfinder.hub.ui.screens.honors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue
import com.pathfinder.hub.ui.theme.PathfinderGreen
import com.pathfinder.hub.ui.theme.PathfinderRed

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: TestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тест") },
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
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.isFinished -> ResultScreen(state, onFinished)
            state.questions.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center
            ) { Text("Вопросы для этой специализации ещё не добавлены") }
            else -> QuestionView(state, viewModel)
        }
    }
}

@Composable
private fun ResultScreen(state: TestUiState, onFinished: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (state.passed) "Тест пройден!" else "Тест не пройден",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (state.passed) PathfinderGreen else PathfinderRed
        )
        Spacer(Modifier.height(16.dp))
        Text("Результат: ${state.score}%",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Проходной балл: 90%",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Вернуться")
        }
    }
}

@Composable
private fun QuestionView(state: TestUiState, viewModel: TestViewModel) {
    val q = state.questions[state.currentIndex]
    val selected = state.answers[q.id]

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        LinearProgressIndicator(
            progress = { (state.currentIndex + 1).toFloat() / state.questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Вопрос ${state.currentIndex + 1} из ${state.questions.size}",
            style = MaterialTheme.typography.labelLarge, color = PathfinderBlue)
        Spacer(Modifier.height(16.dp))
        Text(q.text, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        q.options.forEach { option ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable { viewModel.selectAnswer(q.id, option) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == option)
                        PathfinderBlue.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selected == option,
                        onClick = { viewModel.selectAnswer(q.id, option) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.currentIndex > 0) {
                OutlinedButton(
                    onClick = viewModel::previous,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Назад") }
            }
            Button(
                onClick = viewModel::next,
                enabled = selected != null,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text(if (state.currentIndex < state.questions.lastIndex) "Далее" else "Завершить")
            }
        }
    }
}
