package com.pathfinder.hub.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(title = "Тема оформления") {
                val options = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = state.theme == value,
                            onClick = { viewModel.onThemeChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label) }
                    }
                }
            }

            SettingsCard(title = "Уведомления") {
                ToggleRow(
                    title = "Push-уведомления",
                    subtitle = "Мгновенные уведомления на устройство",
                    checked = state.prefs?.pushEnabled ?: true,
                    onToggle = viewModel::onPushToggle,
                )
                ToggleRow(
                    title = "Email-уведомления",
                    subtitle = "Дублировать важное на почту",
                    checked = state.prefs?.emailEnabled ?: false,
                    onToggle = viewModel::onEmailToggle,
                )
                ToggleRow(
                    title = "Тихие часы",
                    subtitle = "Не беспокоить ночью (22:00-08:00)",
                    checked = state.prefs?.quietHoursEnabled ?: false,
                    onToggle = viewModel::onQuietHoursToggle,
                )
            }

            SettingsCard(title = "Категории уведомлений") {
                CategoryToggle("Задачи", "tasks", state.prefs?.categoryTasks, viewModel)
                CategoryToggle("Модерация", "moderation", state.prefs?.categoryModeration, viewModel)
                CategoryToggle("Обучение", "learning", state.prefs?.categoryLearning, viewModel)
                CategoryToggle("Геймификация", "gamification", state.prefs?.categoryGamification, viewModel)
                CategoryToggle("События", "events", state.prefs?.categoryEvents, viewModel)
                CategoryToggle("Согласие родителя", "consent", state.prefs?.categoryConsent, viewModel)
                CategoryToggle("Прогресс", "progress", state.prefs?.categoryProgress, viewModel)
                CategoryToggle("Платежи", "payments", state.prefs?.categoryPayments, viewModel)
                CategoryToggle("Контакты", "contact", state.prefs?.categoryContact, viewModel)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun CategoryToggle(
    label: String,
    key: String,
    value: Boolean?,
    vm: SettingsViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value ?: true, onCheckedChange = { vm.onCategoryToggle(key, it) })
    }
}
