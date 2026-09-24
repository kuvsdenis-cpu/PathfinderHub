package com.pathfinder.hub.data.local.entity.core
import androidx.room.*
import java.util.Date

@Entity(tableName = "conferences")
data class ConferenceEntity(
    @PrimaryKey val id: String, val name: String, val territory: String,
    val logoUrl: String?, val contactEmail: String, val contactPhone: String,
    val clubLeader: String, val dipkutor: String, val secretary: String,
    val updatedAt: Date, val updatedBy: String, val syncedAt: Date? = null
)
