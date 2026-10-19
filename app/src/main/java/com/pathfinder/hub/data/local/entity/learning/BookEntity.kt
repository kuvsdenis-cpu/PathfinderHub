package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val level: Int,
    val description: String?,
    /** Ссылка на внешний ресурс (сайт, PDF, EGW Writings). */
    val externalUrl: String? = null,
    /** Путь к локальному тексту (assets/... или files/...). */
    val contentPath: String? = null,
    /** Обложка (URL или asset-путь). */
    val coverUrl: String? = null,
    /** null — глобальная книга; иначе — clubId клуба, добавившего книгу. */
    val clubId: String? = null,
    /** uid автора (для клубных книг, null для global). */
    val createdBy: String? = null,
    /** timestamp создания (мс). */
    val createdAt: Long? = null,
    /** true — есть несинхронизированные изменения (для клубных книг). */
    val pendingSync: Boolean = false
)
