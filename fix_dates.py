#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Date/Time pickers (L) - 09.10.2026

- NEW: ui/components/DateTimePicker.kt (дата + время)
- NEW: ui/components/DatePickerField.kt (только дата)
- REWRITE: CreateEventViewModel + CreateEventScreen
- REWRITE: CreateReportViewModel + CreateReportScreen

Формат отображения: dd.MM.yyyy / dd.MM.yyyy HH:mm
Storage: Date (как было)
Запуск:
    python fix_dates.py
"""

import shutil, sys
from datetime import datetime
from pathlib import Path

ROOT = Path.cwd()
STAMP = datetime.now().strftime("%Y%m%d_%H%M%S")


def die(m): print("[ERROR] " + m); sys.exit(1)


def write_new(path, content):
    p = ROOT / path
    if p.exists():
        print("[SKIP] " + path + " (уже есть)")
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[NEW]  " + path)


def rewrite(path, content):
    p = ROOT / path
    if not p.exists():
        die("не найден: " + path)
    bak = p.with_suffix(p.suffix + ".bak." + STAMP)
    shutil.copy2(p, bak)
    print("[BAK]  " + bak.name)
    p.write_text(content, encoding="utf-8", newline="\n")
    print("[REWRITE] " + path)


# ============================================================
# 1. DateTimePicker.kt
# ============================================================
DT = '''package com.pathfinder.hub.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DISPLAY_FMT = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))

/**
 * Поле выбора даты и времени.
 * Отображение: dd.MM.yyyy HH:mm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    value: Date?,
    label: String,
    onValueChange: (Date) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<Date?>(null) }

    val displayText = value?.let { DISPLAY_FMT.format(it) } ?: ""

    OutlinedTextField(
        value = displayText,
        onValueChange = { },
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(
                onClick = { if (enabled) showDatePicker = true },
                enabled = enabled
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Выбрать дату")
            }
        }
    )

    if (showDatePicker) {
        val initialMs = value?.let { dateToUtcMillis(it) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) {
                        pendingDate = utcMillisToLocalDate(ms)
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("Далее") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val base = pendingDate ?: value ?: Date()
        val cal = Calendar.getInstance().apply { time = base }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Время") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply {
                        time = base
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onValueChange(c.time)
                    showTimePicker = false
                    pendingDate = null
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    pendingDate = null
                }) { Text("Отмена") }
            }
        )
    }
}

private fun dateToUtcMillis(date: Date): Long {
    val ldt = Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()).toLocalDate()
    return ldt.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun utcMillisToLocalDate(utcMillis: Long): Date {
    val ld: LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val cal = Calendar.getInstance()
    cal.set(ld.year, ld.monthValue - 1, ld.dayOfMonth, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/components/DateTimePicker.kt", DT)


# ============================================================
# 2. DatePickerField.kt
# ============================================================
DP = '''package com.pathfinder.hub.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DISPLAY_FMT = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

/**
 * Поле выбора только даты (без времени).
 * Отображение: dd.MM.yyyy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: Date?,
    label: String,
    onValueChange: (Date) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayText = value?.let { DISPLAY_FMT.format(it) } ?: ""

    OutlinedTextField(
        value = displayText,
        onValueChange = { },
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        modifier = modifier,
        trailingIcon = {
            IconButton(
                onClick = { if (enabled) showPicker = true },
                enabled = enabled
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Выбрать дату")
            }
        }
    )

    if (showPicker) {
        val initialMs = value?.let { dateToUtcMillis(it) }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(utcMillisToLocalDate(it)) }
                    showPicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private fun dateToUtcMillis(date: Date): Long {
    val ld = Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()).toLocalDate()
    return ld.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun utcMillisToLocalDate(utcMillis: Long): Date {
    val ld: LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val cal = Calendar.getInstance()
    cal.set(ld.year, ld.monthValue - 1, ld.dayOfMonth, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}
'''
write_new("app/src/main/java/com/pathfinder/hub/ui/components/DatePickerField.kt", DP)


# ============================================================
# 3. CreateEventViewModel.kt — REWRITE
# ============================================================
EV_VM = '''package com.pathfinder.hub.ui.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateEventUiState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val dateStart: Date? = null,
    val dateEnd: Date? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateEventUiState())
    val state: StateFlow<CreateEventUiState> = _state.asStateFlow()

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onLocationChange(v: String) = _state.update { it.copy(location = v) }
    fun onDateStartChange(v: Date) = _state.update { it.copy(dateStart = v) }
    fun onDateEndChange(v: Date) = _state.update { it.copy(dateEnd = v) }

    fun createEvent() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите название") }
            return
        }
        if (s.dateStart == null || s.dateEnd == null) {
            _state.update { it.copy(errorMessage = "Укажите дату начала и окончания") }
            return
        }
        if (s.dateEnd.before(s.dateStart)) {
            _state.update { it.copy(errorMessage = "Окончание раньше начала") }
            return
        }

        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                val event = EventEntity(
                    id = UUID.randomUUID().toString(),
                    level = "club",
                    clubId = clubId,
                    type = "meeting",
                    title = s.title.trim(),
                    description = s.description.trim(),
                    dateStart = s.dateStart,
                    dateEnd = s.dateEnd,
                    timezone = java.util.TimeZone.getDefault().id,
                    location = s.location.trim(),
                    isRecurring = false,
                    recurrenceRule = null,
                    parentEventId = null,
                    createdBy = userId,
                    status = "published",
                    autoModerationResult = "approved",
                    moderatedBy = userId,
                    moderatedAt = Date(),
                    reviewDeadline = null,
                    hiddenReasonCode = null,
                    hiddenReasonNote = null,
                    visibility = "club",
                    publishedAt = Date(),
                )

                eventRepository.upsert(event)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/events/CreateEventViewModel.kt", EV_VM)


# ============================================================
# 4. CreateEventScreen.kt — REWRITE
# ============================================================
EV_SCREEN = '''package com.pathfinder.hub.ui.screens.events

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateTimeField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое событие") },
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
            Text("Основная информация", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

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

            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Место проведения") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

            Spacer(Modifier.height(24.dp))
            Text("Дата и время", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            DateTimeField(
                value = state.dateStart,
                label = "Начало",
                onValueChange = viewModel::onDateStartChange,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            DateTimeField(
                value = state.dateEnd,
                label = "Окончание",
                onValueChange = viewModel::onDateEndChange,
                enabled = !state.isLoading
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::createEvent,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Создать событие")
                }
            }
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/screens/events/CreateEventScreen.kt", EV_SCREEN)


# ============================================================
# 5. CreateReportViewModel.kt — REWRITE
# ============================================================
RP_VM = '''package com.pathfinder.hub.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.reports.ReportEntity
import com.pathfinder.hub.data.repository.ReportRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class CreateReportUiState(
    val level: String = "club",
    val periodStart: Date? = null,
    val periodEnd: Date? = null,
    val format: String = "pdf",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreated: Boolean = false,
)

@HiltViewModel
class CreateReportViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateReportUiState())
    val state: StateFlow<CreateReportUiState> = _state.asStateFlow()

    fun onLevelChange(v: String) = _state.update { it.copy(level = v) }
    fun onPeriodStartChange(v: Date) = _state.update { it.copy(periodStart = v) }
    fun onPeriodEndChange(v: Date) = _state.update { it.copy(periodEnd = v) }
    fun onFormatChange(v: String) = _state.update { it.copy(format = v) }

    fun generateReport() {
        val s = _state.value
        if (s.periodStart == null || s.periodEnd == null) {
            _state.update { it.copy(errorMessage = "Укажите период отчёта") }
            return
        }
        if (s.periodEnd.before(s.periodStart)) {
            _state.update { it.copy(errorMessage = "Конец периода раньше начала") }
            return
        }

        val userId = sessionManager.getUserId()
        if (userId == null) {
            _state.update { it.copy(errorMessage = "Пользователь не авторизован") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userId)
                val clubId = currentUser?.clubId

                val report = ReportEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = "default_template",
                    level = s.level,
                    clubId = clubId,
                    periodStart = s.periodStart,
                    periodEnd = s.periodEnd,
                    format = s.format,
                    status = "draft",
                    fileUrl = null,
                    generatedBy = userId,
                    generatedAt = Date(),
                    signed = false,
                    signedAt = null,
                    errorMessage = null,
                )

                reportRepository.upsertReport(report)
                _state.update { it.copy(isLoading = false, isCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка создания")
                }
            }
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/reports/CreateReportViewModel.kt", RP_VM)


# ============================================================
# 6. CreateReportScreen.kt — REWRITE
# ============================================================
RP_SCREEN = '''package com.pathfinder.hub.ui.reports

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.components.DateField
import com.pathfinder.hub.ui.theme.PathfinderBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isCreated) {
        if (state.isCreated) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый отчёт") },
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
            Text("Параметры отчёта", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.level,
                onValueChange = viewModel::onLevelChange,
                label = { Text("Уровень (club/teen)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )
            Spacer(Modifier.height(12.dp))

            DateField(
                value = state.periodStart,
                label = "Начало периода",
                onValueChange = viewModel::onPeriodStartChange,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            DateField(
                value = state.periodEnd,
                label = "Конец периода",
                onValueChange = viewModel::onPeriodEndChange,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.format,
                onValueChange = viewModel::onFormatChange,
                label = { Text("Формат (pdf/csv)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::generateReport,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Сгенерировать отчёт")
                }
            }
        }
    }
}
'''
rewrite("app/src/main/java/com/pathfinder/hub/ui/reports/CreateReportScreen.kt", RP_SCREEN)


print()
print("=" * 60)
print("Готово. Сборка:")
print("  .\\gradlew assembleDebug")
print("  .\\gradlew installDebug")
print("=" * 60)
print()
print("Проверка:")
print("  1. Директор → События → 'Создать событие'")
print("  2. Поля 'Начало' и 'Окончание' → иконка календаря справа")
print("  3. Открывается календарь → выбрать → время → ОК")
print("  4. Формат dd.MM.yyyy HH:mm")
print("  5. Отчёты → 'Начало периода' / 'Конец периода' → только дата")
print()
print("Откат: .bak.<timestamp>")