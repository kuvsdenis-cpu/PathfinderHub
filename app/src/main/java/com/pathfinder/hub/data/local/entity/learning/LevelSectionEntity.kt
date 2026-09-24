package com.pathfinder.hub.data.local.entity.learning
import androidx.room.*

@Entity(tableName = "level_sections",
    foreignKeys = [ForeignKey(LevelEntity::class, ["id"], ["levelId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("levelId")])
data class LevelSectionEntity(
    @PrimaryKey val id: String, val levelId: String, val name: String, val order: Int
)
