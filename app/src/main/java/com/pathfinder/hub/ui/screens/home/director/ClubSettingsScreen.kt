package com.pathfinder.hub.ui.screens.home.director

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.data.local.entity.core.ClubEntity
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubSettingsScreen(
    onBack: () -> Unit,
    viewModel: ClubSettingsViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val clubState by clubContextViewModel.state.collectAsState()
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            ClubSettingsContent(
                onBack = onBack,
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                clubContextViewModel = clubContextViewModel,
            )
        }
    }
}

@Composable
fun ClubSettingsContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClubSettingsViewModel = hiltViewModel(),
    clubContextViewModel: ClubContextViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clubState by clubContextViewModel.state.collectAsState()
    var editedClub by remember { mutableStateOf<ClubEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.club?.id) {
        if (state.club != null) {
            editedClub = state.club
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.errorMessage != null && editedClub == null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                Alignment.Center
            ) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
            editedClub != null -> {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Основная информация", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value = editedClub!!.name, onValueChange = { editedClub = editedClub!!.copy(name = it) }, label = { Text("Название клуба") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.city, onValueChange = { editedClub = editedClub!!.copy(city = it) }, label = { Text("Город") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.region, onValueChange = { editedClub = editedClub!!.copy(region = it) }, label = { Text("Регион") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.contactEmail, onValueChange = { editedClub = editedClub!!.copy(contactEmail = it) }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.contactPhone, onValueChange = { editedClub = editedClub!!.copy(contactPhone = it) }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.motto ?: "", onValueChange = { editedClub = editedClub!!.copy(motto = it) }, label = { Text("Девиз") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(24.dp))
                        Text("Расписание встреч", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(value = editedClub!!.meetingDay, onValueChange = { editedClub = editedClub!!.copy(meetingDay = it) }, label = { Text("День встреч") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = editedClub!!.startTime, onValueChange = { editedClub = editedClub!!.copy(startTime = it) }, label = { Text("Начало") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = editedClub!!.endTime, onValueChange = { editedClub = editedClub!!.copy(endTime = it) }, label = { Text("Окончание") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.location, onValueChange = { editedClub = editedClub!!.copy(location = it) }, label = { Text("Место проведения") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = editedClub!!.frequency, onValueChange = { editedClub = editedClub!!.copy(frequency = it) }, label = { Text("Частота") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.updateClub(editedClub!!) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            if (state.isSaving) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Сохранить")
                            }
                        }
                        if (state.saveSuccess) {
                            Spacer(Modifier.height(12.dp))
                            Text("Изменения сохранены", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.errorMessage != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.currentUserIsOwner) {
                            Spacer(Modifier.height(32.dp))
                            Text("Опасная зона", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Удалить клуб", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Клуб и всё его содержимое будут скрыты. Восстановить можно в течение 90 дней.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { showDeleteDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Удалить клуб")
                                    }
                                }
                            }
                        }
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить клуб?") },
            text = { Text("Клуб будет скрыт из всех списков. Восстановить можно в течение 90 дней, потом он удалится навсегда.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteClub(onDeleted = onBack)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}
