package com.pathfinder.hub.ui.screens.home.director.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.EventOccurrenceEntity
import com.pathfinder.hub.data.repository.EventRepository
import com.pathfinder.hub.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class DirectorCalendarUiState(
    val isLoading: Boolean = true,
    val monthStartMillis: Long = 0L,
    val daysInMonth: Int = 30,
    val monthLabel: String = "",
    val todayMillis: Long = 0L,
    val selectedDayMillis: Long = 0L,
    val eventDays: Set<Long> = emptySet(),
    val eventsForSelectedDay: List<EventEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class DirectorCalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DirectorCalendarUiState())
    val state: StateFlow<DirectorCalendarUiState> = _state.asStateFlow()

    private var clubId: String? = null
    private var allEvents: List<EventEntity> = emptyList()
    private var allOccurrences: List<EventOccurrenceEntity> = emptyList()
    private var currentMonthCal: Calendar = Calendar.getInstance()

    init {
        val uid = sessionManager.getUserId()
        if (uid == null) {
            _state.update { it.copy(isLoading = false, errorMessage = "Нет сессии") }
        } else {
            viewModelScope.launch {
                val user = userRepository.getUser(uid)
                clubId = user?.clubId ?: user?.clubIds?.firstOrNull()
                if (clubId == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Клуб не найден") }
                } else {
                    initCalendar()
                }
            }
        }
    }

    private fun initCalendar() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        currentMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        _state.update {
            it.copy(
                monthStartMillis = currentMonthCal.timeInMillis,
                daysInMonth = currentMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH),
                monthLabel = formatMonth(currentMonthCal),
                todayMillis = today.timeInMillis,
                selectedDayMillis = today.timeInMillis,
            )
        }
        loadEventsForMonth()
    }

    private fun loadEventsForMonth() {
        val cid = clubId ?: return
        val cal = currentMonthCal.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 3)
        val to = cal.timeInMillis
        viewModelScope.launch {
            try {
                allEvents = eventRepository.observeEventsInRange(cid, from, to).first()
                val recurringIds = allEvents.filter { it.isRecurring }.map { it.id }
                allOccurrences = if (recurringIds.isEmpty()) emptyList()
                    else try { eventRepository.getOccurrencesForEvents(recurringIds, from, to) }
                         catch (_: Exception) { emptyList() }
                applyEventsToState()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun applyEventsToState() {
        val daySet = mutableSetOf<Long>()
        allEvents.forEach { ev ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = ev.dateStart.time
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            daySet.add(cal.timeInMillis)
        }
        allOccurrences.forEach { occ ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = occ.dateStart.time
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            daySet.add(cal.timeInMillis)
        }
        val selected = _state.value.selectedDayMillis
        val eventById = allEvents.associateBy { it.id }
        val items = mutableListOf<EventEntity>()
        // События, у которых dateStart в этом дне
        allEvents.forEach { e ->
            if (isSameDay(e.dateStart.time, selected)) items.add(e)
        }
        // Occurrence в этом дне — клон event с dateStart = occurrence.dateStart
        allOccurrences.forEach { occ ->
            if (isSameDay(occ.dateStart.time, selected)) {
                val e = eventById[occ.eventId] ?: return@forEach
                if (items.none { it.id == e.id && it.dateStart.time == occ.dateStart.time }) {
                    items.add(e.copy(dateStart = occ.dateStart, dateEnd = occ.dateEnd))
                }
            }
        }
        items.sortBy { it.dateStart }
        _state.update {
            it.copy(
                isLoading = false,
                eventDays = daySet,
                eventsForSelectedDay = items,
            )
        }
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun selectDay(millis: Long) {
        _state.update { it.copy(selectedDayMillis = millis) }
        applyEventsToState()
    }

    fun prevMonth() {
        currentMonthCal.add(Calendar.MONTH, -1)
        updateMonth()
        loadEventsForMonth()
    }

    fun nextMonth() {
        currentMonthCal.add(Calendar.MONTH, 1)
        updateMonth()
        loadEventsForMonth()
    }

    private fun updateMonth() {
        _state.update {
            it.copy(
                monthStartMillis = currentMonthCal.timeInMillis,
                daysInMonth = currentMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH),
                monthLabel = formatMonth(currentMonthCal),
            )
        }
    }

    private fun formatMonth(cal: Calendar): String {
        val fmt = SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("ru"))
        val s = fmt.format(cal.time)
        return s.replaceFirstChar { it.uppercaseChar() }
    }
}
