package com.pathfinder.hub.data.readingplan

import com.google.gson.annotations.SerializedName

data class ReadingPlanJsonDoc(
    val year: Int = 0,
    val kind: String = "BIBLE",
    val title: String? = null,
    val version: Int? = null,
    val days: List<ReadingPlanDayJson> = emptyList(),
)

data class ReadingPlanDayJson(
    val dayNumber: Int = 0,
    val date: String? = null,
    val reference: String? = null,
    @SerializedName("bookId") val bookId: String? = null,
    @SerializedName("chapterFrom") val chapterFrom: Int? = null,
    @SerializedName("chapterTo") val chapterTo: Int? = null,
    @SerializedName("passageText") val passageText: String? = null,
)
