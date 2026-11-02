package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreBookDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreBookDS"
    }

    private fun booksRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection("books")

    fun observeClubBooks(clubId: String): Flow<RemoteSnapshot<BookEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = false))
            awaitClose { }
            return@callbackFlow
        }
        val registration = booksRef(clubId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "clubBooks listener failed", err)
                    trySend(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
                    return@addSnapshotListener
                }
                val fromCache = snap?.metadata?.isFromCache ?: true
                val list = snap?.documents?.mapNotNull { doc ->
                    try { mapBook(doc.id, doc.data ?: emptyMap(), clubId) }
                    catch (e: Exception) { Log.e(TAG, "map book failed ${doc.id}", e); null }
                } ?: emptyList()
                trySend(RemoteSnapshot(list, fromCache, hasError = false))
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeClubBooks flow error", e)
        emit(RemoteSnapshot(emptyList(), fromCache = false, hasError = true))
    }

    suspend fun pushBook(book: BookEntity): Result<Unit> = try {
        val clubId = book.clubId ?: return Result.failure(
            IllegalArgumentException("clubId is null")
        )
        booksRef(clubId).document(book.id).set(mapBookToMap(book)).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "pushBook failed ${book.id}", e)
        Result.failure(e)
    }

    suspend fun deleteBook(clubId: String, bookId: String): Result<Unit> = try {
        booksRef(clubId).document(bookId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "deleteBook failed $bookId", e)
        Result.failure(e)
    }

    private fun mapBook(id: String, d: Map<String, Any?>, clubId: String): BookEntity =
        BookEntity(
            id = id,
            title = d["title"] as? String ?: "",
            author = d["author"] as? String ?: "",
            level = (d["level"] as? Number)?.toInt() ?: 1,
            description = d["description"] as? String,
            externalUrl = d["externalUrl"] as? String,
            contentPath = d["contentPath"] as? String,
            coverUrl = d["coverUrl"] as? String,
            clubId = (d["clubId"] as? String) ?: clubId,
            createdBy = d["createdBy"] as? String,
            createdAt = (d["createdAt"] as? Number)?.toLong(),
            pendingSync = false,
        )

    private fun mapBookToMap(book: BookEntity): Map<String, Any?> = mapOf(
        "title" to book.title,
        "author" to book.author,
        "level" to book.level,
        "description" to book.description,
        "externalUrl" to book.externalUrl,
        "contentPath" to book.contentPath,
        "coverUrl" to book.coverUrl,
        "clubId" to book.clubId,
        "createdBy" to book.createdBy,
        "createdAt" to book.createdAt,
    )
}
