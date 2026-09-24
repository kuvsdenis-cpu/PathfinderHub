package com.pathfinder.hub.domain.usecase.planning

import com.pathfinder.hub.data.local.entity.planning.EventOccurrenceEntity
import com.pathfinder.hub.data.repository.EventRepository
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class GenerateOccurrencesUseCase @Inject constructor(
    private val eventRepo: EventRepository
) {
    suspend operator fun invoke(eventId: String, weeksAhead: Int = 8): Result<Int> {
        return try {
            val event = eventRepo.getEvent(eventId)
                ?: return Result.failure(Exception("Event not found"))

            if (!event.isRecurring) return Result.success(0)

            val occurrences = mutableListOf<EventOccurrenceEntity>()
            var current = event.dateStart.time
            val end = current + weeksAhead * 7L * 24 * 3600 * 1000
            val duration = event.dateEnd.time - event.dateStart.time

            while (current <= end) {
                occurrences.add(
                    EventOccurrenceEntity(
                        id = UUID.randomUUID().toString(),
                        eventId = eventId,
                        dateStart = Date(current),
                        dateEnd = Date(current + duration),
                        status = "planned",
                        overrideData = null
                    )
                )
                current += 7L * 24 * 3600 * 1000
            }

            eventRepo.upsertOccurrences(occurrences)
            Result.success(occurrences.size)
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}