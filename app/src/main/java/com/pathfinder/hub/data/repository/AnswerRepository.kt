package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.remote.FirestoreAnswerDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnswerRepository @Inject constructor(
    private val firestore: FirestoreAnswerDataSource,
) {
    fun observePendingAnswers(clubId: String): Flow<List<RequirementTaskAnswerEntity>> =
        firestore.observePendingAnswers(clubId)

    suspend fun approve(answer: RequirementTaskAnswerEntity, reviewerId: String) =
        firestore.updateStatus(
            answer.clubId, answer.id,
            RequirementTaskAnswerEntity.STATUS_APPROVED,
            reviewerId, null,
        )

    suspend fun reject(answer: RequirementTaskAnswerEntity, reviewerId: String, comment: String) =
        firestore.updateStatus(
            answer.clubId, answer.id,
            RequirementTaskAnswerEntity.STATUS_REJECTED,
            reviewerId, comment,
        )
}
