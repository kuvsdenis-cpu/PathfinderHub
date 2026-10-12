package com.pathfinder.hub.ui.screens.homework

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateHomeworkScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateHomeworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая домашка") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.teenCount > 0) {
                Text(
                    "Будет назначено ${state.teenCount} следопытам",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PathfinderBlue
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            DateField(
                value = state.dueDate,
                label = "Срок",
                onValueChange = viewModel::onDueDateChange,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Text("Тип награды", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.rewardType == "text",
                    onClick = { viewModel.onRewardTypeChange("text") },
                    label = { Text("Текст") },
                )
                FilterChip(
                    selected = state.rewardType == "points",
                    onClick = { viewModel.onRewardTypeChange("points") },
                    label = { Text("Баллы") },
                )
                FilterChip(
                    selected = state.rewardType == "achievement",
                    onClick = { viewModel.onRewardTypeChange("achievement") },
                    label = { Text("Ачивка") },
                )
            }
            Spacer(Modifier.height(8.dp))

            when (state.rewardType) {
                "text" -> OutlinedTextField(
                    value = state.rewardText,
                    onValueChange = viewModel::onRewardTextChange,
                    label = { Text("Текст награды (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isLoading,
                )
                "points" -> OutlinedTextField(
                    value = state.rewardPoints,
                    onValueChange = viewModel::onRewardPointsChange,
                    label = { Text("Баллы") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                "achievement" -> OutlinedTextField(
                    value = state.rewardAchievementId,
                    onValueChange = viewModel::onRewardAchievementIdChange,
                    label = { Text("ID ачивки") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isLoading,
                )
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::create,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Назначить всем следопытам")
                }
            }
        }
    }
}
