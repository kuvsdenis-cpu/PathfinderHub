package com.pathfinder.hub.ui.pearls

import com.pathfinder.hub.data.local.entity.bible.PearlEntity
import com.pathfinder.hub.data.local.entity.bible.PearlProgressEntity

data class PearlsUiState(
    val isLoading: Boolean = true,
    val scheduleId: String? = null,
    val scheduleTitle: String = "",
    val currentPearl: PearlEntity? = null,
    val seasons: List<SeasonSection> = emptyList(),
    val progress: Map<String, PearlProgressEntity> = emptyMap(),
    val noSchedule: Boolean = false,
)

data class SeasonSection(
    val number: Int,
    val title: String,
    val pearls: List<PearlEntity>,
)

data class PearlDetailUiState(
    val isLoading: Boolean = true,
    val pearl: PearlEntity? = null,
    val progress: PearlProgressEntity? = null,
)
