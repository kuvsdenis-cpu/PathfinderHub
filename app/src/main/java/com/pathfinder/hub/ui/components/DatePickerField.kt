package com.pathfinder.hub.ui.components

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
