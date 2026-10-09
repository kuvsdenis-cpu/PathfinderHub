package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.gamification.ChallengeEntity
import com.pathfinder.hub.data.local.entity.gamification.ChallengeProgressEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для челленджей и прогресса.
 *
 * Коллекции:
 *   clubs/{clubId}/challenges/{challengeId}
 *   clubs/{clubId}/challengeProgress/{challengeId}_{userId}
 *
 * Даты → Timestamp. Composite id для прогресса гарантирует,
 * что у одного пользователя — одна запись на челлендж.
 */
@Singleton
class FirestoreChallengeDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreChDS"
        const val COL_CHALLENGES = "challenges"
        const val COL_PROGRESS = "challengeProgress"

        fun progressId(challengeId: String, userId: String): String =
            "${challengeId}_${userId}"
    }

    private fun challengesRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COL_CHALLENGES)

    private fun progressRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COL_PROGRESS)

    suspend fun upsertChallenge(c: ChallengeEntity): Result<Unit> = try {
        if (c.clubId.isBlank()) throw IllegalArgumentException("clubId пустой")
        challengesRef(c.clubId).document(c.id).set(mapChallenge(c)).await()
        Log.d(TAG, "challenge upsert OK: ${c.clubId}/${c.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "challenge upsert FAILED: ${c.id}", e)
        Result.failure(e)
    }

    suspend fun upsertProgress(clubId: String, p: ChallengeProgressEntity): Result<Unit> = try {
        if (clubId.isBlank()) throw IllegalArgumentException("clubId пустой")
        val id = progressId(p.challengeId, p.userId)
        progressRef(clubId).document(id).set(mapProgress(p, clubId)).await()
        Log.d(TAG, "progress upsert OK: $clubId/$id")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "progress upsert FAILED: ${p.challengeId}", e)
        Result.failure(e)
    }

    private fun mapChallenge(c: ChallengeEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "clubId" to c.clubId,
        "title" to c.title,
        "description" to c.description,
        "type" to c.type,
        "goal" to c.goal,
        "unit" to c.unit,
        "dateStart" to Timestamp(c.dateStart),
        "dateEnd" to Timestamp(c.dateEnd),
        "createdBy" to c.createdBy,
        "status" to c.status,
        "participants" to c.participants,
        "rewards" to c.rewards,
    )

    private fun mapProgress(p: ChallengeProgressEntity, clubId: String): Map<String, Any?> = mapOf(
        "challengeId" to p.challengeId,
        "userId" to p.userId,
        "clubId" to clubId,
        "progress" to p.progress,
        "completedAt" to p.completedAt?.let { Timestamp(it) },
        "rewardClaimed" to p.rewardClaimed,
    )
}
