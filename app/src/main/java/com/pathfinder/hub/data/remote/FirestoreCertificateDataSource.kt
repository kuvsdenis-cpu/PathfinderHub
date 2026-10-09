package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pathfinder.hub.data.local.entity.gamification.CertificateEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для сертификатов.
 * Коллекция: users/{userId}/certificates/{certId}
 */
@Singleton
class FirestoreCertificateDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreCertDS"
        const val COL = "certificates"
    }

    private fun certsRef(userId: String) =
        firestore.collection("users").document(userId).collection(COL)

    suspend fun upsert(c: CertificateEntity): Result<Unit> = try {
        certsRef(c.userId).document(c.id).set(map(c)).await()
        Log.d(TAG, "cert upsert OK: ${c.userId}/${c.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "cert upsert FAILED: ${c.id}", e)
        Result.failure(e)
    }

    private fun map(c: CertificateEntity): Map<String, Any?> = mapOf(
        "id" to c.id,
        "userId" to c.userId,
        "type" to c.type,
        "targetId" to c.targetId,
        "issuedAt" to Timestamp(c.issuedAt),
        "pdfUrl" to c.pdfUrl,
        "imageUrl" to c.imageUrl,
    )
}
