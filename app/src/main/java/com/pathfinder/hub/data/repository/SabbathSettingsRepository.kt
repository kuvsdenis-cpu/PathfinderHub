package com.pathfinder.hub.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sabbathDataStore by preferencesDataStore(name = "sabbath_settings")

data class SabbathSettings(
    val year: String = "B",
    val quarter: Int = 4,
    val autoAdvance: Boolean = true,
    val manualLessonOverride: Int? = null,
)

@Singleton
class SabbathSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val YEAR = stringPreferencesKey("year")
        val QUARTER = intPreferencesKey("quarter")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val MANUAL_LESSON = intPreferencesKey("manual_lesson")
    }

    val settings: Flow<SabbathSettings> = context.sabbathDataStore.data.map { prefs ->
        SabbathSettings(
            year = prefs[Keys.YEAR] ?: "B",
            quarter = prefs[Keys.QUARTER] ?: 4,
            autoAdvance = prefs[Keys.AUTO_ADVANCE] ?: true,
            manualLessonOverride = prefs[Keys.MANUAL_LESSON]?.takeIf { it in 1..13 },
        )
    }

    suspend fun setYear(year: String) {
        context.sabbathDataStore.edit { it[Keys.YEAR] = year }
    }

    suspend fun setQuarter(quarter: Int) {
        context.sabbathDataStore.edit { it[Keys.QUARTER] = quarter }
    }

    suspend fun setAutoAdvance(enabled: Boolean) {
        context.sabbathDataStore.edit { it[Keys.AUTO_ADVANCE] = enabled }
    }

    suspend fun setManualLesson(lesson: Int?) {
        context.sabbathDataStore.edit { prefs ->
            if (lesson == null) prefs.remove(Keys.MANUAL_LESSON)
            else prefs[Keys.MANUAL_LESSON] = lesson
        }
    }

    suspend fun currentLesson(): Int {
        val s = settings.first()
        if (!s.autoAdvance) return s.manualLessonOverride ?: 1
        return autoLessonFor(s.year, s.quarter)
    }

    private fun autoLessonFor(year: String, quarter: Int): Int {
        val yearNum = when (year) {
            "A" -> 2024
            "B" -> 2025
            "V" -> 2026
            "G" -> 2027
            else -> 2025
        }
        val cal = Calendar.getInstance()
        cal.set(yearNum, (quarter - 1) * 3, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        val firstSaturday = cal.timeInMillis
        val now = Date().time
        if (now < firstSaturday) return 1
        val weeks = ((now - firstSaturday) / (7L * 24L * 60L * 60L * 1000L)).toInt() + 1
        return weeks.coerceIn(1, 13)
    }
}
