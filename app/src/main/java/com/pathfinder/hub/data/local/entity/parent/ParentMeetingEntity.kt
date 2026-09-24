package com.pathfinder.hub.data.local.entity.parent
import androidx.room.*

@Entity(tableName = "parent_meetings")
data class ParentMeetingEntity(
    @PrimaryKey val eventId: String, val clubId: String,
    val agenda: String, val attendees: List<ParentAttendee>
)

data class ParentAttendee(
    val parentId: String, val confirmed: Boolean, val questions: List<String>
)
