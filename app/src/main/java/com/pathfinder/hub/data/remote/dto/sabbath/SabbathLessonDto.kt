package com.pathfinder.hub.data.remote.dto.sabbath

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SabbathLessonDto(
    val lesson_id: String,
    val year: String? = null,
    val quarter: Int? = null,
    val quarter_title: String,
    val lesson_number: Int,
    val title: String,
    val introduction: String,
    val memory_verse: MemoryVerseDto,
    val sections: List<SectionDto>
)

@JsonClass(generateAdapter = true)
data class MemoryVerseDto(
    val text: String,
    val reference: String,
    val answers: List<String>
)

@JsonClass(generateAdapter = true)
data class SectionDto(
    val title: String,
    val blocks: List<BlockDto>
)

@JsonClass(generateAdapter = true)
data class BlockDto(
    val type: String,
    val content: String? = null,
    val id: String? = null,
    val placeholder: String? = null
)
