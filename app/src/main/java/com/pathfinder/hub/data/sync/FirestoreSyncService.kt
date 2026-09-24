package com.pathfinder.hub.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncService @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun pushDocument(
        collection: String,
        documentId: String,
        data: Map<String, Any?>
    ): Result<Unit> {
        return try {
            firestore.collection(collection)
                .document(documentId)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Push OK: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Push FAIL: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(
        collection: String,
        documentId: String
    ): Result<Unit> {
        return try {
            firestore.collection(collection)
                .document(documentId)
                .delete()
                .await()
            Log.d(TAG, "Delete OK: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Delete FAIL: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    suspend fun pullDocument(
        collection: String,
        documentId: String
    ): Result<Map<String, Any?>> {
        return try {
            val snapshot = firestore.collection(collection)
                .document(documentId)
                .get()
                .await()
            if (snapshot.exists()) {
                Result.success(snapshot.data ?: emptyMap())
            } else {
                Result.failure(Exception("Document not exists"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull FAIL: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    suspend fun pullCollection(
        collection: String
    ): Result<List<Map<String, Any?>>> {
        return try {
            val snapshot = firestore.collection(collection).get().await()
            Result.success(snapshot.documents.mapNotNull { it.data })
        } catch (e: Exception) {
            Log.e(TAG, "Pull collection FAIL: $collection", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FirestoreSync"
    }
}
