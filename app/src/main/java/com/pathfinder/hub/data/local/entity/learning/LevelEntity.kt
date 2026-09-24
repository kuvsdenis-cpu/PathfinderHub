package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "levels")
data class LevelEntity(
    @PrimaryKey val id: String, val name: String, val order: Int,
    val ageMin: Int, val grade: Int, val icon: String?, val contentVersion: String
)
