package com.pathfinder.hub.data.local.entity.planning
import androidx.room.*

@Entity(tableName = "event_honors", primaryKeys = ["eventId", "honorId"])
data class EventHonorEntity(
    val eventId: String, val honorId: String, val instructorId: String,
    val stationQr: String?, val requirements: List<String>
)
