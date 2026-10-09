package com.pathfinder.hub.ui.screens.payments

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePaymentScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreatePaymentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый взнос") },
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
            // ---------- Ребёнок ----------
            var teenExpanded by remember { mutableStateOf(false) }
            val teenText = state.teens.find { it.teen.id == state.selectedTeenId }
                ?.teen?.let { "${it.firstName} ${it.lastName}".trim() }
                ?: "Выберите ребёнка"

            ExposedDropdownMenuBox(
                expanded = teenExpanded,
                onExpandedChange = { teenExpanded = !teenExpanded }
            ) {
                OutlinedTextField(
                    value = teenText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ребёнок") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving
                )
                ExposedDropdownMenu(
                    expanded = teenExpanded,
                    onDismissRequest = { teenExpanded = false }
                ) {
                    state.teens.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${opt.teen.firstName} ${opt.teen.lastName}".trim() +
                                            if (opt.parents.isEmpty()) " (нет родителя)" else ""
                                )
                            },
                            onClick = {
                                viewModel.onTeenSelected(opt.teen.id)
                                teenExpanded = false
                            }
                        )
                    }
                }
            }

            // ---------- Родитель (если у teen >1) ----------
            val selectedOpt = state.teens.find { it.teen.id == state.selectedTeenId }
            if (selectedOpt != null && selectedOpt.parents.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text("Родитель", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row {
                    selectedOpt.parents.forEach { parent ->
                        FilterChip(
                            selected = state.selectedParentId == parent.id,
                            onClick = { viewModel.onParentSelected(parent.id) },
                            label = { Text("${parent.firstName} ${parent.lastName}".trim()) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Назначение (например, 'Взнос за октябрь')") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSaving
            )

            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::onAmount,
                    label = { Text("Сумма") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    enabled = !state.isSaving
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = state.currency,
                    onValueChange = viewModel::onCurrency,
                    label = { Text("Валюта") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !state.isSaving
                )
            }

            Spacer(Modifier.height(12.dp))
            DateField(
                value = state.dueDate,
                label = "Срок оплаты",
                onValueChange = viewModel::onDueDate,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Тип", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row {
                listOf(
                    "membership" to "Взнос",
                    "event" to "Мероприятие",
                    "other" to "Другое",
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.type == key,
                        onClick = { viewModel.onType(key) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.immediatelyPaid,
                    onCheckedChange = viewModel::onImmediatelyPaid,
                    enabled = !state.isSaving
                )
                Text("Уже оплачено (закрыть сразу)")
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
                        msg, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Создать взнос")
                }
            }
        }
    }
}
