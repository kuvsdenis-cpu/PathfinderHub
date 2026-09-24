package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "level_requirements",
    foreignKeys = [ForeignKey(LevelSectionEntity::class, ["id"], ["sectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sectionId"), Index("levelId")])
data class LevelRequirementEntity(
    @PrimaryKey val id: String, val sectionId: String, val levelId: String,
    val text: String, val type: String, val order: Int, val isAdvanced: Boolean
)
