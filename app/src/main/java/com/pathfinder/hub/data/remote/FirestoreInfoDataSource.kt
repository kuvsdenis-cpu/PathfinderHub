package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pathfinder.hub.data.local.entity.info.InfoArticleEntity
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreInfoDataSource @Inject constructor() {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("info_articles")
    private val TAG = "FirestoreInfoDS"

    suspend fun push(article: InfoArticleEntity): Result<Unit> = try {
        col.document(article.id).set(
            mapOf(
                "id" to article.id,
                "categoryId" to article.categoryId,
                "title" to article.title,
                "content" to article.content,
                "pdfUrl" to article.pdfUrl,
                "createdAt" to article.createdAt.time,
                "updatedAt" to article.updatedAt.time,
                "deletedAt" to article.deletedAt?.time
            ), SetOptions.merge()
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "push failed", e); Result.failure(e)
    }

    suspend fun pullAll(): List<InfoArticleEntity> = try {
        val snap = col.get().await()
        snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            InfoArticleEntity(
                id = doc.id,
                categoryId = d["categoryId"] as? String ?: "",
                title = d["title"] as? String ?: "",
                content = d["content"] as? String ?: "",
                pdfUrl = d["pdfUrl"] as? String,
                createdAt = Date((d["createdAt"] as? Number)?.toLong() ?: 0L),
                updatedAt = Date((d["updatedAt"] as? Number)?.toLong() ?: 0L),
                deletedAt = (d["deletedAt"] as? Number)?.let { Date(it.toLong()) },
                pendingSync = false
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "pull failed", e); emptyList()
    }
}
