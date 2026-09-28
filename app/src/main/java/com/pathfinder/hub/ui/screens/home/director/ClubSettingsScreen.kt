package com.pathfinder.hub.ui.screens.home.director

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubSettingsScreen(
    onBack: () -> Unit,
    viewModel: ClubSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var editedClub by remember { mutableStateOf<ClubEntity?>(null) }

    LaunchedEffect(state.club) {
        if (state.club != null && editedClub == null) {
            editedClub = state.club
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки клуба") },
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
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null && editedClub == null -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            editedClub != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Основная информация", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editedClub!!.name,
                        onValueChange = { editedClub = editedClub!!.copy(name = it) },
                        label = { Text("Название клуба") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.city,
                        onValueChange = { editedClub = editedClub!!.copy(city = it) },
                        label = { Text("Город") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.region,
                        onValueChange = { editedClub = editedClub!!.copy(region = it) },
                        label = { Text("Регион") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.contactEmail,
                        onValueChange = { editedClub = editedClub!!.copy(contactEmail = it) },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.contactPhone,
                        onValueChange = { editedClub = editedClub!!.copy(contactPhone = it) },
                        label = { Text("Телефон") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.motto ?: "",
                        onValueChange = { editedClub = editedClub!!.copy(motto = it) },
                        label = { Text("Девиз") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(24.dp))
                    Text("Расписание встреч", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editedClub!!.meetingDay,
                        onValueChange = { editedClub = editedClub!!.copy(meetingDay = it) },
                        label = { Text("День встреч") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editedClub!!.startTime,
                            onValueChange = { editedClub = editedClub!!.copy(startTime = it) },
                            label = { Text("Начало") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editedClub!!.endTime,
                            onValueChange = { editedClub = editedClub!!.copy(endTime = it) },
                            label = { Text("Окончание") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.location,
                        onValueChange = { editedClub = editedClub!!.copy(location = it) },
                        label = { Text("Место проведения") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedClub!!.frequency,
                        onValueChange = { editedClub = editedClub!!.copy(frequency = it) },
                        label = { Text("Частота") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.updateClub(editedClub!!) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Сохранить")
                        }
                    }

                    if (state.saveSuccess) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Изменения сохранены",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (state.errorMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSuccess()
        }
    }
}