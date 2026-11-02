package com.pathfinder.hub.data.local.entity.core

import androidx.room.Entity
import androidx.room.Index

/**
 * Связь «пользователь — клуб» (многие-ко-многим).
 *
 * Введена в B1 как источник правды для мультиклубности.
 * Дублирует денормализованные users.clubId / users.clubIds,
 * которые остаются для UI-кэша.
 *
 * Синхронизация — в UserRepository (upsert / update /
 * addClubToUser / removeClubFromUser / setActiveClub).
 */
@Entity(
    tableName = "user_clubs",
    primaryKeys = ["userId", "clubId"],
    indices = [Index("userId"), Index("clubId")]
)
data class UserClubEntity(
    val userId: String,
    val clubId: String,
)
