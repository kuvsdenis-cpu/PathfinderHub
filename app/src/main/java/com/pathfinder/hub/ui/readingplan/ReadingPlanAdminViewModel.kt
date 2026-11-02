package com.pathfinder.hub.ui.readingplan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanDayEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanScheduleEntity
import com.pathfinder.hub.data.readingplan.ReadingPlanJsonDoc
import com.pathfinder.hub.data.repository.ReadingPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class ReadingPlanAdminUiState(
    val isLoading: Boolean = true,
    val schedules: List<ReadingPlanScheduleEntity> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ReadingPlanAdminViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReadingPlanRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val gson = Gson()
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _state = MutableStateFlow(ReadingPlanAdminUiState())
    val state: StateFlow<ReadingPlanAdminUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAllSchedules().collect { list ->
                _state.update { it.copy(isLoading = false, schedules = list) }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun exportJson(scheduleId: String) {
        viewModelScope.launch {
            try {
                val schedule = _state.value.schedules.find { it.id == scheduleId } ?: return@launch
                val first = repo.observeAllDays(scheduleId).first()
                val doc = ReadingPlanJsonDoc(
                    year = schedule.year,
                    kind = schedule.kind,
                    title = schedule.title,
                    version = schedule.version,
                    days = first.map { d ->
                        com.pathfinder.hub.data.readingplan.ReadingPlanDayJson(
                            dayNumber = d.dayNumber,
                            date = isoFmt.format(d.date),
                            reference = d.reference,
                            bookId = d.bookId,
                            chapterFrom = d.chapterFrom,
                            chapterTo = d.chapterTo,
                            passageText = d.passageText,
                        )
                    }
                )
                val json = gson.toJson(doc)
                val file = java.io.File(context.cacheDir, "reading_plan_${schedule.id}.json")
                file.writeText(json)
                _state.update {
                    it.copy(message = "Экспортировано: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка экспорта: ${e.message}") }
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("Не удалось прочитать файл")

                val doc = gson.fromJson(json, ReadingPlanJsonDoc::class.java)
                if (doc == null || doc.days.isEmpty()) {
                    throw IllegalStateException("Пустой JSON")
                }

                val kind = doc.kind.ifBlank { "BIBLE" }
                val scheduleId = "${kind.lowercase()}-${doc.year}"
                val uid = sessionManager.getUserId()

                repo.upsertSchedule(
                    ReadingPlanScheduleEntity(
                        id = scheduleId,
                        year = doc.year,
                        title = doc.title ?: "План $kind ${doc.year}",
                        kind = kind,
                        version = doc.version ?: 1,
                        source = "imported",
                        importedAt = System.currentTimeMillis(),
                        importedBy = uid,
                        isActive = true,
                    )
                )

                val days = doc.days.mapNotNull { d ->
                    val dateStr = d.date ?: return@mapNotNull null
                    ReadingPlanDayEntity(
                        id = "$scheduleId:${d.dayNumber}",
                        scheduleId = scheduleId,
                        dayNumber = d.dayNumber,
                        date = isoFmt.parse(dateStr)?.time ?: System.currentTimeMillis(),
                        reference = d.reference ?: "",
                        bookId = d.bookId,
                        chapterFrom = d.chapterFrom,
                        chapterTo = d.chapterTo,
                        passageText = d.passageText,
                    )
                }
                repo.deleteDaysForSchedule(scheduleId)
                repo.upsertDays(days)

                _state.update {
                    it.copy(message = "Импортировано: $scheduleId, ${days.size} дней")
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка импорта: ${e.message}") }
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            try {
                repo.deleteDaysForSchedule(scheduleId)
                _state.update { it.copy(message = "Удалено: $scheduleId") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка удаления: ${e.message}") }
            }
        }
    }
}
