package com.pathfinder.hub.ui.screens.home.conference.events

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.pathfinder.hub.ui.components.DateTimeField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateConferenceEventScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateConferenceEventViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое мероприятие") },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Название *") },
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
                minLines = 3,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::onLocation,
                label = { Text("Место проведения") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            Text("Уровень", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row {
                listOf(
                    "conference" to "Конференция",
                    "regional" to "Регион",
                    "meeting" to "Собрание",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.level == key,
                        onClick = { viewModel.onLevel(key) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            DateTimeField(
                value = state.dateStart,
                label = "Начало",
                onValueChange = viewModel::onDateStart,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))
            DateTimeField(
                value = state.dateEnd,
                label = "Окончание",
                onValueChange = viewModel::onDateEnd,
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
                        msg, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Создать мероприятие")
                }
            }
        }
    }
}
