package com.pathfinder.hub.ui.qr

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStationScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateStationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая QR-станция") },
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            Text("Тип станции", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row {
                FilterChip(
                    selected = state.type == "entry",
                    onClick = { viewModel.onType("entry") },
                    label = { Text("Вход") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.type == "quest",
                    onClick = { viewModel.onType("quest") },
                    label = { Text("Квест") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.type == "custom",
                    onClick = { viewModel.onType("custom") },
                    label = { Text("Другое") }
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardPoints,
                onValueChange = viewModel::onRewardPoints,
                label = { Text("Очки за посещение") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.rewardAchievementId,
                onValueChange = viewModel::onRewardAchievement,
                label = { Text("ID ачивки (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.latitude,
                onValueChange = viewModel::onLatitude,
                label = { Text("Широта") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.longitude,
                onValueChange = viewModel::onLongitude,
                label = { Text("Долгота") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.radiusMeters,
                onValueChange = viewModel::onRadius,
                label = { Text("Радиус (м)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

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
                    Text("Создать станцию")
                }
            }
        }
    }
}
