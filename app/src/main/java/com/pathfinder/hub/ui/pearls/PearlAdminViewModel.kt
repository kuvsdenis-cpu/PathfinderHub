package com.pathfinder.hub.ui.pearls

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.bible.PearlEntity
import com.pathfinder.hub.data.local.entity.bible.PearlScheduleEntity
import com.pathfinder.hub.data.pearls.PearlsJsonDoc
import com.pathfinder.hub.data.repository.PearlRepository
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

data class PearlAdminUiState(
    val isLoading: Boolean = true,
    val schedules: List<PearlScheduleEntity> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class PearlAdminViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: PearlRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val gson = Gson()
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _state = MutableStateFlow(PearlAdminUiState())
    val state: StateFlow<PearlAdminUiState> = _state.asStateFlow()

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
                val pearls = repo.observeAllPearls(scheduleId).first()
                val seasonTitles = pearls.groupBy { it.season }
                    .map { (num, list) ->
                        com.pathfinder.hub.data.pearls.SeasonJson(
                            number = num,
                            title = list.firstOrNull()?.seasonTitle,
                            weeksFrom = list.minOfOrNull { it.weekNumber },
                            weeksTo = list.maxOfOrNull { it.weekNumber },
                        )
                    }
                val doc = PearlsJsonDoc(
                    year = schedule.year,
                    title = schedule.title,
                    version = schedule.version,
                    seasons = seasonTitles,
                    pearls = pearls.map { p ->
                        com.pathfinder.hub.data.pearls.PearlJson(
                            week = p.weekNumber,
                            reference = p.reference,
                            verseId = p.verseId,
                            text = p.text,
                            start = isoFmt.format(p.startDate),
                            end = isoFmt.format(p.endDate),
                            isExam = p.isExam,
                        )
                    }
                )
                val json = gson.toJson(doc)
                val file = java.io.File(context.cacheDir, "pearls_${schedule.id}.json")
                file.writeText(json)
                _state.update { it.copy(message = "Экспортировано: ${file.absolutePath}") }
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

                val doc = gson.fromJson(json, PearlsJsonDoc::class.java)
                if (doc == null || doc.pearls.isEmpty()) {
                    throw IllegalStateException("Пустой JSON")
                }

                val scheduleId = doc.year.toString()
                val uid = sessionManager.getUserId()

                repo.upsertSchedule(
                    PearlScheduleEntity(
                        id = scheduleId,
                        year = doc.year,
                        title = doc.title ?: "Жемчужины ${doc.year}",
                        version = doc.version ?: 1,
                        source = "imported",
                        importedAt = System.currentTimeMillis(),
                        importedBy = uid,
                        isActive = true,
                    )
                )

                val seasonTitles = doc.seasons.associate { it.number to (it.title ?: "") }
                val pearls = doc.pearls.mapNotNull { p ->
                    val week = p.week ?: return@mapNotNull null
                    val season = doc.seasons.firstOrNull {
                        week >= (it.weeksFrom ?: 0) && week <= (it.weeksTo ?: 0)
                    }?.number ?: 0
                    PearlEntity(
                        id = "$scheduleId:$week",
                        scheduleId = scheduleId,
                        weekNumber = week,
                        season = season,
                        seasonTitle = seasonTitles[season] ?: "",
                        verseId = p.verseId,
                        reference = p.reference ?: "",
                        text = p.text,
                        startDate = isoFmt.parse(p.start ?: "2026-01-01")?.time
                            ?: System.currentTimeMillis(),
                        endDate = isoFmt.parse(p.end ?: "2026-01-07")?.time
                            ?: System.currentTimeMillis(),
                        isExam = p.isExam ?: false,
                    )
                }
                repo.deletePearlsForSchedule(scheduleId)
                repo.upsertPearls(pearls)

                _state.update {
                    it.copy(message = "Импортировано: $scheduleId, ${pearls.size} жемчужин")
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка импорта: ${e.message}") }
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            try {
                repo.deletePearlsForSchedule(scheduleId)
                _state.update { it.copy(message = "Удалено: $scheduleId") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка удаления: ${e.message}") }
            }
        }
    }
}
