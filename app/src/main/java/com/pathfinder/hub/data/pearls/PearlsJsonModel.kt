package com.pathfinder.hub.data.pearls

import com.google.gson.annotations.SerializedName

data class PearlsJsonDoc(
    val year: Int = 0,
    val title: String? = null,
    val version: Int? = null,
    val seasons: List<SeasonJson> = emptyList(),
    val pearls: List<PearlJson> = emptyList(),
)

data class SeasonJson(
    val number: Int = 0,
    val title: String? = null,
    @SerializedName("weeksFrom") val weeksFrom: Int? = null,
    @SerializedName("weeksTo") val weeksTo: Int? = null,
)

data class PearlJson(
    val week: Int? = null,
    val reference: String? = null,
    @SerializedName("verseId") val verseId: String? = null,
    val text: String? = null,
    val start: String? = null,
    val end: String? = null,
    @SerializedName("isExam") val isExam: Boolean? = null,
)
