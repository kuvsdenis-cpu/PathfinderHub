package com.pathfinder.hub.ui.readingplan

import com.pathfinder.hub.data.local.entity.bible.ReadingPlanDayEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanProgressEntity
import com.pathfinder.hub.data.local.entity.bible.ReadingPlanScheduleEntity

data class ReadingPlansUiState(
    val isLoading: Boolean = true,
    val schedules: List<ReadingPlanScheduleEntity> = emptyList(),
)

data class ReadingPlanDetailUiState(
    val isLoading: Boolean = true,
    val schedule: ReadingPlanScheduleEntity? = null,
    val currentDay: ReadingPlanDayEntity? = null,
    val days: List<ReadingPlanDayEntity> = emptyList(),
    val progress: Map<String, ReadingPlanProgressEntity> = emptyMap(),
)

data class ReadingPlanDayUiState(
    val isLoading: Boolean = true,
    val day: ReadingPlanDayEntity? = null,
    val progress: ReadingPlanProgressEntity? = null,
)
