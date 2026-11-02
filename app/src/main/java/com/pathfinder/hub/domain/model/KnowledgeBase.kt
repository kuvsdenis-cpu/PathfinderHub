package com.pathfinder.hub.domain.model

data class InfoCategory(
    val id: String,
    val title: String,
    val icon: String,
    val articles: List<InfoArticle>
)

data class InfoArticle(
    val id: String,
    val title: String,
    val content: String,
    val imageRes: List<Int> = emptyList(), // Список R.drawable.*
    val pdfAsset: String? = null // Имя файла в assets/
)
