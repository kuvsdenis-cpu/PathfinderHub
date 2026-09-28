package com.pathfinder.hub.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit, // <-- ВЕРНУЛИ () -> Unit
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.registerSuccess) {
        if (state.registerSuccess) {
            onRegisterSuccess() // <-- ВЫЗЫВАЕМ БЕЗ ПАРАМЕТРОВ
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Регистрация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState())
        ) {
            Text("Инвайт-код", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.inviteCode,
                onValueChange = viewModel::onInviteCodeChange,
                label = { Text("Код") },
                singleLine = true,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::validateInvite,
                enabled = state.inviteCode.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Проверить код") }

            if (state.clubId != null && state.role != null) {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Код принят", fontWeight = FontWeight.SemiBold)
                        Text("Роль: ${state.role}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text("Личные данные", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(value = state.firstName, onValueChange = viewModel::onFirstNameChange, label = { Text("Имя") }, singleLine = true, enabled = !state.isLoading && state.clubId != null, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = state.lastName, onValueChange = viewModel::onLastNameChange, label = { Text("Фамилия") }, singleLine = true, enabled = !state.isLoading && state.clubId != null, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = state.email, onValueChange = viewModel::onEmailChange, label = { Text("Email") }, singleLine = true, enabled = !state.isLoading && state.clubId != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = state.birthDate, onValueChange = viewModel::onBirthDateChange, label = { Text("Дата рождения (YYYY-MM-DD)") }, singleLine = true, enabled = !state.isLoading && state.clubId != null, modifier = Modifier.fillMaxWidth())

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Text(text = state.errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::register,
                enabled = state.clubId != null && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Зарегистрироваться")
                }
            }
        }
    }
}