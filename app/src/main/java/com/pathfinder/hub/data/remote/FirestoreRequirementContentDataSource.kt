package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для сопутствующих материалов и проверочных заданий.
 *
 * Пути:
 *   clubs/{clubId}/requirements/{requirementId}/materials/{materialId}
 *   clubs/{clubId}/requirements/{requirementId}/tasks/{taskId}
 *
 * Создаёт/правит/удаляет только директор.
 * Наставник может только flag/unflag (пометка "надо поправить").
 */
@Singleton
class FirestoreRequirementContentDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreReqContentDS"
    }

    private fun materialsRef(clubId: String, requirementId: String) =
        firestore.collection("clubs").document(clubId)
            .collection("requirements").document(requirementId)
            .collection("materials")

    private fun tasksRef(clubId: String, requirementId: String) =
        firestore.collection("clubs").document(clubId)
            .collection("requirements").document(requirementId)
            .collection("tasks")

    // ==================== MATERIALS ====================

    fun observeMaterials(
        clubId: String,
        requirementId: String,
    ): Flow<List<RequirementMaterialEntity>> = callbackFlow {
        if (clubId.isBlank() || requirementId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = materialsRef(clubId, requirementId)
            .orderBy("orderIndex", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "materials listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        mapMaterial(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "material map failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMaterials flow error", e)
        emit(emptyList())
    }

    suspend fun upsertMaterial(material: RequirementMaterialEntity): Result<Unit> = try {
        materialsRef(material.clubId, material.requirementId)
            .document(material.id)
            .set(materialToMap(material))
            .await()
        Log.d(TAG, "material upsert OK: ${material.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "material upsert FAIL: ${material.id}", e)
        Result.failure(e)
    }

    suspend fun deleteMaterial(clubId: String, requirementId: String, materialId: String): Result<Unit> = try {
        materialsRef(clubId, requirementId).document(materialId).delete().await()
        Log.d(TAG, "material delete OK: $materialId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "material delete FAIL: $materialId", e)
        Result.failure(e)
    }

    suspend fun flagMaterial(
        clubId: String,
        requirementId: String,
        materialId: String,
        by: String,
        comment: String?,
    ): Result<Unit> = try {
        materialsRef(clubId, requirementId).document(materialId)
            .update(
                mapOf(
                    "flaggedForEdit" to true,
                    "flaggedBy" to by,
                    "flaggedAt" to Timestamp(Date()),
                    "flagComment" to comment
                )
            ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "flagMaterial FAIL", e)
        Result.failure(e)
    }

    suspend fun unflagMaterial(
        clubId: String,
        requirementId: String,
        materialId: String,
    ): Result<Unit> = try {
        materialsRef(clubId, requirementId).document(materialId)
            .update(
                mapOf(
                    "flaggedForEdit" to false,
                    "flaggedBy" to null,
                    "flaggedAt" to null,
                    "flagComment" to null
                )
            ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "unflagMaterial FAIL", e)
        Result.failure(e)
    }

    // ==================== TASKS ====================

    fun observeTasks(
        clubId: String,
        requirementId: String,
    ): Flow<List<RequirementTaskEntity>> = callbackFlow {
        if (clubId.isBlank() || requirementId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = tasksRef(clubId, requirementId)
            .orderBy("orderIndex", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "tasks listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        mapTask(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "task map failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeTasks flow error", e)
        emit(emptyList())
    }

    suspend fun upsertTask(task: RequirementTaskEntity): Result<Unit> = try {
        tasksRef(task.clubId, task.requirementId)
            .document(task.id)
            .set(taskToMap(task))
            .await()
        Log.d(TAG, "task upsert OK: ${task.id}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "task upsert FAIL: ${task.id}", e)
        Result.failure(e)
    }

    suspend fun deleteTask(clubId: String, requirementId: String, taskId: String): Result<Unit> = try {
        tasksRef(clubId, requirementId).document(taskId).delete().await()
        Log.d(TAG, "task delete OK: $taskId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "task delete FAIL: $taskId", e)
        Result.failure(e)
    }

    suspend fun flagTask(
        clubId: String,
        requirementId: String,
        taskId: String,
        by: String,
        comment: String?,
    ): Result<Unit> = try {
        tasksRef(clubId, requirementId).document(taskId)
            .update(
                mapOf(
                    "flaggedForEdit" to true,
                    "flaggedBy" to by,
                    "flaggedAt" to Timestamp(Date()),
                    "flagComment" to comment
                )
            ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "flagTask FAIL", e)
        Result.failure(e)
    }

    suspend fun unflagTask(
        clubId: String,
        requirementId: String,
        taskId: String,
    ): Result<Unit> = try {
        tasksRef(clubId, requirementId).document(taskId)
            .update(
                mapOf(
                    "flaggedForEdit" to false,
                    "flaggedBy" to null,
                    "flaggedAt" to null,
                    "flagComment" to null
                )
            ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "unflagTask FAIL", e)
        Result.failure(e)
    }

    // ==================== PULL (для SyncWorker) ====================

    /**
     * Один раз тянет все материалы требования. Используется в SyncWorker
     * для прогрева Room у других участников клуба (listener в observeMaterials
     * работает только пока экран открыт).
     */
    suspend fun pullMaterials(
        clubId: String,
        requirementId: String,
    ): List<RequirementMaterialEntity> = try {
        materialsRef(clubId, requirementId).get().await().documents.mapNotNull { doc ->
            try {
                mapMaterial(doc.id, doc.data ?: emptyMap())
            } catch (e: Exception) {
                Log.e(TAG, "pullMaterials map failed ${doc.id}", e)
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "pullMaterials FAIL: $clubId/$requirementId", e)
        emptyList()
    }

    /**
     * Аналог pullMaterials для заданий.
     */
    suspend fun pullTasks(
        clubId: String,
        requirementId: String,
    ): List<RequirementTaskEntity> = try {
        tasksRef(clubId, requirementId).get().await().documents.mapNotNull { doc ->
            try {
                mapTask(doc.id, doc.data ?: emptyMap())
            } catch (e: Exception) {
                Log.e(TAG, "pullTasks map failed ${doc.id}", e)
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "pullTasks FAIL: $clubId/$requirementId", e)
        emptyList()
    }

    // ==================== МАППИНГ ====================

    private fun mapMaterial(id: String, d: Map<String, Any?>): RequirementMaterialEntity {
        return RequirementMaterialEntity(
            id = id,
            requirementId = d["requirementId"] as? String ?: "",
            clubId = d["clubId"] as? String ?: "",
            type = d["type"] as? String ?: "text",
            title = d["title"] as? String ?: "",
            content = d["content"] as? String ?: "",
            orderIndex = (d["orderIndex"] as? Number)?.toInt() ?: 0,
            flaggedForEdit = d["flaggedForEdit"] as? Boolean ?: false,
            flaggedBy = d["flaggedBy"] as? String,
            flaggedAt = parseDate(d["flaggedAt"]),
            flagComment = d["flagComment"] as? String,
            createdBy = d["createdBy"] as? String,
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            updatedAt = parseDate(d["updatedAt"]) ?: Date(),
            pendingSync = false
        )
    }

    private fun materialToMap(m: RequirementMaterialEntity): Map<String, Any?> = mapOf(
        "id" to m.id,
        "requirementId" to m.requirementId,
        "clubId" to m.clubId,
        "type" to m.type,
        "title" to m.title,
        "content" to m.content,
        "orderIndex" to m.orderIndex,
        "flaggedForEdit" to m.flaggedForEdit,
        "flaggedBy" to m.flaggedBy,
        "flaggedAt" to m.flaggedAt?.let { Timestamp(it) },
        "flagComment" to m.flagComment,
        "createdBy" to m.createdBy,
        "createdAt" to Timestamp(m.createdAt),
        "updatedAt" to Timestamp(m.updatedAt)
    )

    private fun mapTask(id: String, d: Map<String, Any?>): RequirementTaskEntity {
        return RequirementTaskEntity(
            id = id,
            requirementId = d["requirementId"] as? String ?: "",
            clubId = d["clubId"] as? String ?: "",
            type = d["type"] as? String ?: "short_text",
            title = d["title"] as? String ?: "",
            description = d["description"] as? String,
            options = (d["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            correctAnswer = d["correctAnswer"] as? String,
            autoCheck = d["autoCheck"] as? Boolean ?: false,
            isRequired = d["isRequired"] as? Boolean ?: true,
            orderIndex = (d["orderIndex"] as? Number)?.toInt() ?: 0,
            flaggedForEdit = d["flaggedForEdit"] as? Boolean ?: false,
            flaggedBy = d["flaggedBy"] as? String,
            flaggedAt = parseDate(d["flaggedAt"]),
            flagComment = d["flagComment"] as? String,
            createdBy = d["createdBy"] as? String,
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            updatedAt = parseDate(d["updatedAt"]) ?: Date(),
            pendingSync = false
        )
    }

    private fun taskToMap(t: RequirementTaskEntity): Map<String, Any?> = mapOf(
        "id" to t.id,
        "requirementId" to t.requirementId,
        "clubId" to t.clubId,
        "type" to t.type,
        "title" to t.title,
        "description" to t.description,
        "options" to t.options,
        "correctAnswer" to t.correctAnswer,
        "autoCheck" to t.autoCheck,
        "isRequired" to t.isRequired,
        "orderIndex" to t.orderIndex,
        "flaggedForEdit" to t.flaggedForEdit,
        "flaggedBy" to t.flaggedBy,
        "flaggedAt" to t.flaggedAt?.let { Timestamp(it) },
        "flagComment" to t.flagComment,
        "createdBy" to t.createdBy,
        "createdAt" to Timestamp(t.createdAt),
        "updatedAt" to Timestamp(t.updatedAt)
    )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
