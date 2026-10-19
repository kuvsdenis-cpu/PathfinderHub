package com.pathfinder.hub.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeenProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: TeenProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Реакция на завершение logout
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
        val u = state.user ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "${u.firstName} ${u.lastName}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(u.email, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            StatCard("Пройдено ступеней", state.completedLevels.toString())
            Spacer(Modifier.height(8.dp))
            StatCard("Достижений", state.achievements.toString())

            Spacer(Modifier.height(32.dp))

            // Кнопка выхода
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Выйти")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Диалог подтверждения выхода
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы уверены, что хотите выйти? Для повторного входа потребуется email и пароль.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = PathfinderBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}