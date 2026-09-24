package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "honor_categories")
data class HonorCategoryEntity(
    @PrimaryKey val id: String, val name: String, val order: Int, val icon: String?
)
