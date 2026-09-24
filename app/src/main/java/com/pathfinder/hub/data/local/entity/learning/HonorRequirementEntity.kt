package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "honor_requirements",
    foreignKeys = [ForeignKey(HonorEntity::class, ["id"], ["honorId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("honorId")])
data class HonorRequirementEntity(
    @PrimaryKey val id: String, val honorId: String, val text: String,
    val type: String, val order: Int
)
