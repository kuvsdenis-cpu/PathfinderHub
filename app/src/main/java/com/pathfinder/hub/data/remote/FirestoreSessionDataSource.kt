package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Одна активная сессия на пользователя.
 *
 * Путь: users/{uid}/session/main
 * Поля: activeSessionId, activeSessionAt, deviceModel
 */
@Singleton
class FirestoreSessionDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreSessionDS"
        const val COLLECTION = "session"
        const val DOC_ID = "main"
    }

    private fun docRef(userId: String) =
        firestore.collection("users").document(userId).collection(COLLECTION).document(DOC_ID)

    data class RemoteSession(
        val sessionId: String,
        val sessionAt: Long,
        val deviceModel: String?,
    )

    suspend fun read(userId: String): RemoteSession? = try {
        val snap = docRef(userId).get().await()
        if (!snap.exists()) null
        else {
            val sid = snap.getString("activeSessionId")
            val at = snap.getTimestamp("activeSessionAt")?.toDate()?.time
            val model = snap.getString("deviceModel")
            if (sid != null && at != null) RemoteSession(sid, at, model) else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "read failed for $userId", e)
        null
    }

    suspend fun write(userId: String, sessionId: String, sessionAtMs: Long, deviceModel: String?): Boolean = try {
        docRef(userId).set(
            mapOf(
                "activeSessionId" to sessionId,
                "activeSessionAt" to Timestamp(java.util.Date(sessionAtMs)),
                "deviceModel" to deviceModel,
            )
        ).await()
        Log.d(TAG, "session written: $userId -> ${sessionId.take(8)}…")
        true
    } catch (e: Exception) {
        Log.w(TAG, "write failed for $userId", e)
        false
    }
}
