package com.pathfinder.hub.ui.components

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

private val DISPLAY_FMT = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"))

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
