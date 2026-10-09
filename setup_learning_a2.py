#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pathfinder Hub — Сессия A2.
Создаёт 7 файлов + патчит 5 + firestore.rules.

Запуск из корня проекта (где settings.gradle.kts):
    python3 setup_learning_a2.py
"""

import shutil
import sys
from pathlib import Path


# ============================================================
# ПОИСК КОРНЯ
# ============================================================
def find_project_root() -> Path:
    here = Path.cwd()
    for p in [here] + list(here.parents):
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    print("❌ Не найден корень проекта (settings.gradle.kts).")
    sys.exit(1)


ROOT = find_project_root()
PKG = ROOT / "app" / "src" / "main" / "java" / "com" / "pathfinder" / "hub"

if not PKG.exists():
    print(f"❌ Не найден пакет: {PKG}")
    sys.exit(1)


# ============================================================
# НОВЫЕ ФАЙЛЫ
# ============================================================
FILES = {}

# ---------- 1. FirestoreRequirementContentDataSource ----------
FILES["data/remote/FirestoreRequirementContentDataSource.kt"] = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
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
'''

# ---------- 2. FirestoreTaskAnswerDataSource ----------
FILES["data/remote/FirestoreTaskAnswerDataSource.kt"] = '''package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-источник для ответов на проверочные задания.
 *
 * Путь: clubs/{clubId}/requirementTaskAnswers/{userId}_{taskId}
 *
 * - Следопыт читает свои через query whereEqualTo("userId", uid).
 * - Наставник/директор читает очередь через whereEqualTo("status", "submitted").
 * - Один источник данных, без дублирования.
 */
@Singleton
class FirestoreTaskAnswerDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreTaskAnswerDS"
        private const val COLLECTION = "requirementTaskAnswers"

        fun docId(userId: String, taskId: String) = "${userId}_${taskId}"
    }

    private fun answersRef(clubId: String) =
        firestore.collection("clubs").document(clubId).collection(COLLECTION)

    fun observeMyAnswers(
        clubId: String,
        userId: String,
    ): Flow<List<RequirementTaskAnswerEntity>> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = answersRef(clubId)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "myAnswers listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        map(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "answer map failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMyAnswers flow error", e)
        emit(emptyList())
    }

    fun observeMyAnswer(
        clubId: String,
        userId: String,
        taskId: String,
    ): Flow<RequirementTaskAnswerEntity?> = callbackFlow {
        if (clubId.isBlank() || userId.isBlank() || taskId.isBlank()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val registration = answersRef(clubId)
            .document(docId(userId, taskId))
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "myAnswer listener failed", error)
                    trySend(null)
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.takeIf { it.exists() }?.let { map(it.id, it.data ?: emptyMap()) })
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeMyAnswer flow error", e)
        emit(null)
    }

    fun observeReviewQueue(
        clubId: String,
    ): Flow<List<RequirementTaskAnswerEntity>> = callbackFlow {
        if (clubId.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = answersRef(clubId)
            .whereEqualTo("status", RequirementTaskAnswerEntity.STATUS_SUBMITTED)
            .orderBy("submittedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "reviewQueue listener failed", error)
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        map(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "queue map failed ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.catch { e ->
        Log.e(TAG, "observeReviewQueue flow error", e)
        emit(emptyList())
    }

    suspend fun upsertAnswer(answer: RequirementTaskAnswerEntity): Result<Unit> = try {
        if (answer.clubId.isBlank() || answer.userId.isBlank() || answer.taskId.isBlank()) {
            throw IllegalArgumentException("clubId/userId/taskId пустые")
        }
        answersRef(answer.clubId)
            .document(docId(answer.userId, answer.taskId))
            .set(toMap(answer))
            .await()
        Log.d(TAG, "upsertAnswer OK: ${answer.userId}_${answer.taskId}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "upsertAnswer FAIL", e)
        Result.failure(e)
    }

    suspend fun reviewAnswer(answer: RequirementTaskAnswerEntity): Result<Unit> = try {
        answersRef(answer.clubId)
            .document(docId(answer.userId, answer.taskId))
            .set(toMap(answer))
            .await()
        Log.d(TAG, "reviewAnswer OK: ${answer.userId}_${answer.taskId}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "reviewAnswer FAIL", e)
        Result.failure(e)
    }

    // ==================== МАППИНГ ====================

    private fun map(id: String, d: Map<String, Any?>): RequirementTaskAnswerEntity {
        return RequirementTaskAnswerEntity(
            id = id,
            taskId = d["taskId"] as? String ?: "",
            requirementId = d["requirementId"] as? String ?: "",
            userId = d["userId"] as? String ?: "",
            clubId = d["clubId"] as? String ?: "",
            answer = d["answer"] as? String ?: "",
            attachments = (d["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = d["status"] as? String ?: RequirementTaskAnswerEntity.STATUS_DRAFT,
            autoChecked = d["autoChecked"] as? Boolean ?: false,
            submittedAt = parseDate(d["submittedAt"]),
            reviewedBy = d["reviewedBy"] as? String,
            reviewedAt = parseDate(d["reviewedAt"]),
            reviewComment = d["reviewComment"] as? String,
            createdAt = parseDate(d["createdAt"]) ?: Date(),
            updatedAt = parseDate(d["updatedAt"]) ?: Date(),
            pendingSync = false
        )
    }

    private fun toMap(a: RequirementTaskAnswerEntity): Map<String, Any?> = mapOf(
        "id" to a.id,
        "taskId" to a.taskId,
        "requirementId" to a.requirementId,
        "userId" to a.userId,
        "clubId" to a.clubId,
        "answer" to a.answer,
        "attachments" to a.attachments,
        "status" to a.status,
        "autoChecked" to a.autoChecked,
        "submittedAt" to a.submittedAt?.let { Timestamp(it) },
        "reviewedBy" to a.reviewedBy,
        "reviewedAt" to a.reviewedAt?.let { Timestamp(it) },
        "reviewComment" to a.reviewComment,
        "createdAt" to Timestamp(a.createdAt),
        "updatedAt" to Timestamp(a.updatedAt)
    )

    private fun parseDate(v: Any?): Date? = when (v) {
        null -> null
        is Date -> v
        is Timestamp -> v.toDate()
        is Long -> Date(v)
        else -> null
    }
}
'''

# ---------- 3. RequirementContentRepository ----------
FILES["data/repository/RequirementContentRepository.kt"] = '''package com.pathfinder.hub.data.repository

import android.util.Log
import com.pathfinder.hub.data.local.dao.RequirementMaterialDao
import com.pathfinder.hub.data.local.dao.RequirementTaskDao
import com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity
import com.pathfinder.hub.data.remote.FirestoreRequirementContentDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий материалов и заданий требования.
 *
 * Чтение для UI:
 *   Room-first (мгновенно) + Firestore listener (актуализация).
 *
 * Запись:
 *   Только директор. Изменения пишутся в Room + Firestore,
 *   при провале Firestore — в SyncQueue.
 */
@Singleton
class RequirementContentRepository @Inject constructor(
    private val materialDao: RequirementMaterialDao,
    private val taskDao: RequirementTaskDao,
    private val firestore: FirestoreRequirementContentDataSource,
    private val syncQueue: SyncQueueManager,
) {

    companion object {
        private const val TAG = "ReqContentRepo"
        private const val ET_MATERIAL = "requirement_material"
        private const val ET_TASK = "requirement_task"
    }

    // ==================== ЧТЕНИЕ ====================

    fun observeMaterials(clubId: String, requirementId: String): Flow<List<RequirementMaterialEntity>> =
        firestore.observeMaterials(clubId, requirementId)
            .onEach { list ->
                if (list.isNotEmpty()) {
                    try { materialDao.upsertAll(list.map { it.copy(pendingSync = false) }) }
                    catch (e: Exception) { Log.w(TAG, "materialDao.upsertAll failed", e) }
                }
            }

    fun observeTasks(clubId: String, requirementId: String): Flow<List<RequirementTaskEntity>> =
        firestore.observeTasks(clubId, requirementId)
            .onEach { list ->
                if (list.isNotEmpty()) {
                    try { taskDao.upsertAll(list.map { it.copy(pendingSync = false) }) }
                    catch (e: Exception) { Log.w(TAG, "taskDao.upsertAll failed", e) }
                }
            }

    suspend fun getMaterialsFromRoom(clubId: String, requirementId: String): List<RequirementMaterialEntity> =
        materialDao.observeClubOverrides(requirementId, clubId).let { flow ->
            // однократно: getByRequirement + фильтр по clubId
            materialDao.getByRequirement(requirementId).filter { it.clubId == clubId }
        }

    suspend fun getTasksFromRoom(clubId: String, requirementId: String): List<RequirementTaskEntity> =
        taskDao.getByRequirement(requirementId).filter { it.clubId == clubId }

    // ==================== CRUD: MATERIALS ====================

    suspend fun createMaterial(
        clubId: String, requirementId: String, authorId: String,
        type: String, title: String, content: String, orderIndex: Int,
    ): Result<Unit> {
        val now = Date()
        val material = RequirementMaterialEntity(
            id = UUID.randomUUID().toString(),
            requirementId = requirementId,
            clubId = clubId,
            type = type, title = title, content = content,
            orderIndex = orderIndex,
            createdBy = authorId,
            createdAt = now, updatedAt = now,
        )
        return saveMaterial(material)
    }

    suspend fun saveMaterial(material: RequirementMaterialEntity): Result<Unit> {
        val updated = material.copy(updatedAt = Date())
        materialDao.upsert(updated.copy(pendingSync = true))
        val remote = firestore.upsertMaterial(updated)
        if (remote.isSuccess) {
            materialDao.clearPendingSync(updated.id)
        } else {
            syncQueue.enqueue(ET_MATERIAL, updated.id, "upsert", updated)
            Log.w(TAG, "material saved locally, queued for sync")
        }
        return remote
    }

    suspend fun deleteMaterial(clubId: String, requirementId: String, materialId: String): Result<Unit> {
        materialDao.deleteById(materialId)
        val remote = firestore.deleteMaterial(clubId, requirementId, materialId)
        if (remote.isFailure) {
            syncQueue.enqueue(ET_MATERIAL, materialId, "delete",
                mapOf("clubId" to clubId, "requirementId" to requirementId, "id" to materialId))
        }
        return remote
    }

    // ==================== CRUD: TASKS ====================

    suspend fun createTask(
        clubId: String, requirementId: String, authorId: String,
        type: String, title: String, description: String?,
        options: List<String>, correctAnswer: String?,
        isRequired: Boolean, orderIndex: Int,
    ): Result<Unit> {
        val now = Date()
        val task = RequirementTaskEntity(
            id = UUID.randomUUID().toString(),
            requirementId = requirementId, clubId = clubId,
            type = type, title = title, description = description,
            options = options, correctAnswer = correctAnswer,
            autoCheck = RequirementTaskEntity.isAutoCheckType(type),
            isRequired = isRequired, orderIndex = orderIndex,
            createdBy = authorId,
            createdAt = now, updatedAt = now,
        )
        return saveTask(task)
    }

    suspend fun saveTask(task: RequirementTaskEntity): Result<Unit> {
        val updated = task.copy(
            updatedAt = Date(),
            autoCheck = RequirementTaskEntity.isAutoCheckType(task.type)
        )
        taskDao.upsert(updated.copy(pendingSync = true))
        val remote = firestore.upsertTask(updated)
        if (remote.isSuccess) {
            taskDao.clearPendingSync(updated.id)
        } else {
            syncQueue.enqueue(ET_TASK, updated.id, "upsert", updated)
            Log.w(TAG, "task saved locally, queued for sync")
        }
        return remote
    }

    suspend fun deleteTask(clubId: String, requirementId: String, taskId: String): Result<Unit> {
        taskDao.deleteById(taskId)
        val remote = firestore.deleteTask(clubId, requirementId, taskId)
        if (remote.isFailure) {
            syncQueue.enqueue(ET_TASK, taskId, "delete",
                mapOf("clubId" to clubId, "requirementId" to requirementId, "id" to taskId))
        }
        return remote
    }

    // ==================== FLAG / UNFLAG (наставник) ====================

    suspend fun flagMaterial(clubId: String, requirementId: String, materialId: String, by: String, comment: String?): Result<Unit> {
        val now = System.currentTimeMillis()
        materialDao.flag(materialId, by, now, comment)
        return firestore.flagMaterial(clubId, requirementId, materialId, by, comment)
    }

    suspend fun unflagMaterial(clubId: String, requirementId: String, materialId: String): Result<Unit> {
        materialDao.unflag(materialId, System.currentTimeMillis())
        return firestore.unflagMaterial(clubId, requirementId, materialId)
    }

    suspend fun flagTask(clubId: String, requirementId: String, taskId: String, by: String, comment: String?): Result<Unit> {
        val now = System.currentTimeMillis()
        taskDao.flag(taskId, by, now, comment)
        return firestore.flagTask(clubId, requirementId, taskId, by, comment)
    }

    suspend fun unflagTask(clubId: String, requirementId: String, taskId: String): Result<Unit> {
        taskDao.unflag(taskId, System.currentTimeMillis())
        return firestore.unflagTask(clubId, requirementId, taskId)
    }
}
'''

# ---------- 4. TaskAnswerRepository ----------
FILES["data/repository/TaskAnswerRepository.kt"] = '''package com.pathfinder.hub.data.repository

import android.util.Log
import com.pathfinder.hub.data.local.dao.RequirementTaskAnswerDao
import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий ответов на проверочные задания.
 *
 * - Следопыт: saveDraft, submit.
 * - Наставник/директор: reviewQueue, approve, reject.
 */
@Singleton
class TaskAnswerRepository @Inject constructor(
    private val dao: RequirementTaskAnswerDao,
    private val firestore: FirestoreTaskAnswerDataSource,
    private val syncQueue: SyncQueueManager,
) {

    companion object {
        private const val TAG = "TaskAnswerRepo"
        private const val ET_ANSWER = "requirement_task_answer"
    }

    // ==================== ЧТЕНИЕ ====================

    fun observeMyAnswers(clubId: String, userId: String): Flow<List<RequirementTaskAnswerEntity>> =
        firestore.observeMyAnswers(clubId, userId)

    fun observeMyAnswer(clubId: String, userId: String, taskId: String): Flow<RequirementTaskAnswerEntity?> =
        firestore.observeMyAnswer(clubId, userId, taskId)

    fun observeReviewQueue(clubId: String): Flow<List<RequirementTaskAnswerEntity>> =
        firestore.observeReviewQueue(clubId)

    suspend fun getAnswer(userId: String, taskId: String): RequirementTaskAnswerEntity? =
        dao.getByTaskAndUser(taskId, userId)

    suspend fun countApproved(requirementId: String, userId: String): Int =
        dao.countApprovedForRequirement(requirementId, userId)

    // ==================== ЗАПИСЬ (следопыт) ====================

    /**
     * Сохранить черновик (не отправляет на проверку).
     */
    suspend fun saveDraft(
        clubId: String, userId: String, requirementId: String, taskId: String,
        answer: String, attachments: List<String>,
    ): Result<Unit> {
        val now = Date()
        val existing = dao.getByTaskAndUser(taskId, userId)
        val entity = RequirementTaskAnswerEntity(
            id = existing?.id ?: "${userId}_${taskId}",
            taskId = taskId, requirementId = requirementId,
            userId = userId, clubId = clubId,
            answer = answer, attachments = attachments,
            status = RequirementTaskAnswerEntity.STATUS_DRAFT,
            autoChecked = false,
            submittedAt = existing?.submittedAt,
            reviewedBy = existing?.reviewedBy,
            reviewedAt = existing?.reviewedAt,
            reviewComment = existing?.reviewComment,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            pendingSync = true,
        )
        return save(entity)
    }

    /**
     * Отправить на проверку. Если задание с автопроверкой и ответ верный,
     * сразу ставим статус auto_approved.
     */
    suspend fun submit(
        clubId: String, userId: String, requirementId: String, taskId: String,
        answer: String, attachments: List<String>,
        autoCheck: Boolean, correctAnswer: String?,
    ): Result<RequirementTaskAnswerEntity> {
        val now = Date()
        val existing = dao.getByTaskAndUser(taskId, userId)

        val passedAuto = autoCheck && correctAnswer != null &&
            answer.trim().equals(correctAnswer.trim(), ignoreCase = true)

        val status = if (passedAuto)
            RequirementTaskAnswerEntity.STATUS_AUTO_APPROVED
        else
            RequirementTaskAnswerEntity.STATUS_SUBMITTED

        val entity = RequirementTaskAnswerEntity(
            id = existing?.id ?: "${userId}_${taskId}",
            taskId = taskId, requirementId = requirementId,
            userId = userId, clubId = clubId,
            answer = answer, attachments = attachments,
            status = status,
            autoChecked = passedAuto,
            submittedAt = now,
            reviewedBy = null, reviewedAt = null, reviewComment = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            pendingSync = true,
        )

        val saveResult = save(entity)
        return if (saveResult.isSuccess) Result.success(entity)
        else Result.failure(saveResult.exceptionOrNull() ?: Exception("save failed"))
    }

    // ==================== ПРОВЕРКА (наставник/директор) ====================

    suspend fun approve(
        clubId: String, userId: String, taskId: String,
        reviewerId: String,
    ): Result<Unit> {
        val existing = dao.getByTaskAndUser(taskId, userId)
            ?: return Result.failure(Exception("Ответ не найден"))
        val updated = existing.copy(
            status = RequirementTaskAnswerEntity.STATUS_APPROVED,
            reviewedBy = reviewerId, reviewedAt = Date(),
            reviewComment = null,
            updatedAt = Date(), pendingSync = true,
        )
        dao.upsert(updated)
        return pushAndClearQueue(updated)
    }

    suspend fun reject(
        clubId: String, userId: String, taskId: String,
        reviewerId: String, comment: String,
    ): Result<Unit> {
        if (comment.isBlank()) return Result.failure(Exception("Комментарий обязателен"))
        val existing = dao.getByTaskAndUser(taskId, userId)
            ?: return Result.failure(Exception("Ответ не найден"))
        val updated = existing.copy(
            status = RequirementTaskAnswerEntity.STATUS_REJECTED,
            reviewedBy = reviewerId, reviewedAt = Date(),
            reviewComment = comment,
            updatedAt = Date(), pendingSync = true,
        )
        dao.upsert(updated)
        return pushAndClearQueue(updated)
    }

    // ==================== ВНУТРЕННЕЕ ====================

    private suspend fun save(a: RequirementTaskAnswerEntity): Result<Unit> {
        dao.upsert(a)
        val remote = firestore.upsertAnswer(a)
        if (remote.isSuccess) {
            dao.clearPendingSync(a.id)
        } else {
            syncQueue.enqueue(ET_ANSWER, a.id, "upsert", a)
            Log.w(TAG, "answer saved locally, queued for sync")
        }
        return remote
    }

    private suspend fun pushAndClearQueue(a: RequirementTaskAnswerEntity): Result<Unit> {
        val remote = firestore.reviewAnswer(a)
        if (remote.isSuccess) {
            dao.clearPendingSync(a.id)
        } else {
            syncQueue.enqueue(ET_ANSWER, a.id, "upsert", a)
        }
        return remote
    }
}
'''

# ---------- 5. SubmitTaskAnswerUseCase ----------
FILES["domain/usecase/learning/SubmitTaskAnswerUseCase.kt"] = '''package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity
import com.pathfinder.hub.data.repository.TaskAnswerRepository
import javax.inject.Inject

/**
 * Отправка ответа следопытом.
 *
 * Для заданий с autoCheck=true и совпадающим correctAnswer → auto_approved.
 * Иначе → submitted (ждёт ручной проверки).
 */
class SubmitTaskAnswerUseCase @Inject constructor(
    private val repo: TaskAnswerRepository,
) {
    suspend operator fun invoke(
        clubId: String,
        userId: String,
        requirementId: String,
        taskId: String,
        answer: String,
        attachments: List<String>,
        autoCheck: Boolean,
        correctAnswer: String?,
    ): Result<RequirementTaskAnswerEntity> {
        return repo.submit(
            clubId = clubId,
            userId = userId,
            requirementId = requirementId,
            taskId = taskId,
            answer = answer,
            attachments = attachments,
            autoCheck = autoCheck,
            correctAnswer = correctAnswer,
        )
    }
}
'''

# ---------- 6. ReviewTaskAnswerUseCase ----------
FILES["domain/usecase/learning/ReviewTaskAnswerUseCase.kt"] = '''package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.repository.TaskAnswerRepository
import com.pathfinder.hub.data.repository.UserRepository
import javax.inject.Inject

/**
 * Проверка ответа наставником или директором.
 *
 * Права:
 *   - director — всегда
 *   - instructor / secretary — только свой клуб
 *   - остальные — запрещено
 */
class ReviewTaskAnswerUseCase @Inject constructor(
    private val repo: TaskAnswerRepository,
    private val userRepository: UserRepository,
) {

    suspend fun approve(
        clubId: String,
        targetUserId: String,
        taskId: String,
        reviewerId: String,
    ): Result<Unit> {
        if (!canReview(clubId, reviewerId)) {
            return Result.failure(SecurityException("Нет прав на проверку"))
        }
        return repo.approve(clubId, targetUserId, taskId, reviewerId)
    }

    suspend fun reject(
        clubId: String,
        targetUserId: String,
        taskId: String,
        reviewerId: String,
        comment: String,
    ): Result<Unit> {
        if (!canReview(clubId, reviewerId)) {
            return Result.failure(SecurityException("Нет прав на проверку"))
        }
        return repo.reject(clubId, targetUserId, taskId, reviewerId, comment)
    }

    private suspend fun canReview(clubId: String, userId: String): Boolean {
        val user = userRepository.getUser(userId) ?: return false
        return when (user.role) {
            "director" -> true
            "instructor", "secretary" -> user.clubId == clubId
            else -> false
        }
    }
}
'''

# ---------- 7. CheckRequirementCompletionUseCase ----------
FILES["domain/usecase/learning/CheckRequirementCompletionUseCase.kt"] = '''package com.pathfinder.hub.domain.usecase.learning

import com.pathfinder.hub.data.local.dao.RequirementTaskDao
import com.pathfinder.hub.data.repository.TaskAnswerRepository
import javax.inject.Inject

/**
 * Проверяет, все ли обязательные задания требования выполнены.
 *
 * Выполнено, если:
 *   для каждого isRequired=true задания есть ответ
 *   в статусе approved или auto_approved.
 */
class CheckRequirementCompletionUseCase @Inject constructor(
    private val taskDao: RequirementTaskDao,
    private val answerRepo: TaskAnswerRepository,
) {

    suspend operator fun invoke(
        clubId: String,
        userId: String,
        requirementId: String,
    ): Boolean {
        val requiredTasks = taskDao.getRequiredForRequirement(requirementId, clubId)
            .filter { it.clubId == clubId }

        if (requiredTasks.isEmpty()) return true

        val approvedCount = answerRepo.countApproved(requirementId, userId)
        return approvedCount >= requiredTasks.size
    }
}
'''


# ============================================================
# ЗАПИСЬ ФАЙЛОВ
# ============================================================
def write_files() -> None:
    print(f"📁 Корень проекта: {ROOT}")
    print(f"📁 Пакет:          {PKG}\n")

    created, replaced = 0, 0
    for rel, content in FILES.items():
        target = PKG / rel
        target.parent.mkdir(parents=True, exist_ok=True)

        if target.exists():
            backup = target.with_suffix(target.suffix + ".bak")
            shutil.copy2(target, backup)
            print(f"⚠️  Перезаписываю {rel} (бэкап: {backup.name})")
            replaced += 1

        target.write_text(content, encoding="utf-8")
        print(f"✅ {rel}")
        created += 1

    print(f"\n📦 Создано: {created}, бэкапов: {replaced}")


# ============================================================
# ПАТЧ 1: RepositoryModule.kt
# ============================================================
def patch_repository_module() -> bool:
    print("\n🔧 Патчу RepositoryModule.kt...")
    f = PKG / "di/RepositoryModule.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "provideRequirementContentRepo" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Импорты (FirestoreTaskAnswerDataSource и FirestoreRequirementContentDataSource)
    import_anchor = "import com.pathfinder.hub.data.remote.FirestoreCommentDataSource"
    if import_anchor not in text:
        print(f"❌ Не найден якорь импорта: {import_anchor}")
        return False
    if "FirestoreRequirementContentDataSource" not in text:
        text = text.replace(
            import_anchor,
            import_anchor
            + "\nimport com.pathfinder.hub.data.remote.FirestoreRequirementContentDataSource"
            + "\nimport com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource",
            1
        )

    # Провайдеры — вставим перед закрывающей скобкой объекта
    anchor = "    @Provides @Singleton\n    fun provideInviteRepo("
    if anchor not in text:
        print(f"❌ Не найден якорь для вставки провайдеров")
        return False

    new_providers = '''    @Provides @Singleton
    fun provideRequirementContentRepo(
        materialDao: com.pathfinder.hub.data.local.dao.RequirementMaterialDao,
        taskDao: com.pathfinder.hub.data.local.dao.RequirementTaskDao,
        firestore: FirestoreRequirementContentDataSource,
        syncQueue: SyncQueueManager,
    ) = RequirementContentRepository(materialDao, taskDao, firestore, syncQueue)

    @Provides @Singleton
    fun provideTaskAnswerRepo(
        dao: com.pathfinder.hub.data.local.dao.RequirementTaskAnswerDao,
        firestore: FirestoreTaskAnswerDataSource,
        syncQueue: SyncQueueManager,
    ) = TaskAnswerRepository(dao, firestore, syncQueue)

'''

    text = text.replace(anchor, new_providers + anchor, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RepositoryModule.kt пропатчен (бэкап: {backup.name})")
    return True


# ============================================================
# ПАТЧ 2: SyncWorker.kt — новые ветки
# ============================================================
def patch_sync_worker() -> bool:
    print("\n🔧 Патчу SyncWorker.kt...")
    f = PKG / "data/sync/SyncWorker.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")

    if "requirement_material" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Импорты
    import_anchor = "import com.pathfinder.hub.data.remote.FirestoreCommentDataSource"
    if import_anchor not in text:
        print(f"❌ Не найден якорь импорта")
        return False

    new_imports = (
        "\nimport com.pathfinder.hub.data.local.dao.RequirementMaterialDao"
        "\nimport com.pathfinder.hub.data.local.dao.RequirementTaskDao"
        "\nimport com.pathfinder.hub.data.local.dao.RequirementTaskAnswerDao"
        "\nimport com.pathfinder.hub.data.local.entity.learning.RequirementMaterialEntity"
        "\nimport com.pathfinder.hub.data.local.entity.learning.RequirementTaskEntity"
        "\nimport com.pathfinder.hub.data.local.entity.learning.RequirementTaskAnswerEntity"
        "\nimport com.pathfinder.hub.data.remote.FirestoreRequirementContentDataSource"
        "\nimport com.pathfinder.hub.data.remote.FirestoreTaskAnswerDataSource"
    )
    text = text.replace(import_anchor, import_anchor + new_imports, 1)

    # Зависимости конструктора
    ctor_anchor = "    private val moderationDao: ModerationDao,\n) : CoroutineWorker(context, params) {"
    if ctor_anchor not in text:
        print(f"❌ Не найден якорь конструктора")
        return False
    ctor_new = (
        "    private val moderationDao: ModerationDao,\n"
        "    private val materialDao: RequirementMaterialDao,\n"
        "    private val taskDao: RequirementTaskDao,\n"
        "    private val answerDao: RequirementTaskAnswerDao,\n"
        "    private val contentDataSource: FirestoreRequirementContentDataSource,\n"
        "    private val answerDataSource: FirestoreTaskAnswerDataSource,\n"
        ") : CoroutineWorker(context, params) {"
    )
    text = text.replace(ctor_anchor, ctor_new, 1)

    # В processItem — добавить ветки до общего resolveCollectionPath
    proc_anchor = '''            if (item.entityType == "comment") {
                return processComment(item)
            }
'''
    if proc_anchor not in text:
        print("❌ Не найден якорь в processItem для comment")
        return False

    proc_new = '''            if (item.entityType == "comment") {
                return processComment(item)
            }
            if (item.entityType == "requirement_material") {
                return processRequirementMaterial(item)
            }
            if (item.entityType == "requirement_task") {
                return processRequirementTask(item)
            }
            if (item.entityType == "requirement_task_answer") {
                return processRequirementTaskAnswer(item)
            }
'''
    text = text.replace(proc_anchor, proc_new, 1)

    # Добавить функции processRequirementMaterial/Task/Answer перед classifyError
    classify_anchor = "    private fun classifyError(error: Throwable?): ProcessResult {"
    if classify_anchor not in text:
        print("❌ Не найден якорь classifyError")
        return False

    new_funcs = '''    private suspend fun processRequirementMaterial(item: SyncQueueItemEntity): ProcessResult {
        val material = try {
            gson.fromJson(item.payload, RequirementMaterialEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "material deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (material.clubId.isBlank() || material.requirementId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = contentDataSource.upsertMaterial(material)
                if (r.isSuccess) {
                    try { materialDao.clearPendingSync(material.id) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = contentDataSource.deleteMaterial(material.clubId, material.requirementId, material.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processRequirementTask(item: SyncQueueItemEntity): ProcessResult {
        val task = try {
            gson.fromJson(item.payload, RequirementTaskEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "task deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (task.clubId.isBlank() || task.requirementId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = contentDataSource.upsertTask(task)
                if (r.isSuccess) {
                    try { taskDao.clearPendingSync(task.id) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            "delete" -> {
                val r = contentDataSource.deleteTask(task.clubId, task.requirementId, task.id)
                if (r.isSuccess) ProcessResult.SUCCESS else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

    private suspend fun processRequirementTaskAnswer(item: SyncQueueItemEntity): ProcessResult {
        val answer = try {
            gson.fromJson(item.payload, RequirementTaskAnswerEntity::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "answer deserialize failed", e); return ProcessResult.PERMANENT_FAILURE
        } ?: return ProcessResult.PERMANENT_FAILURE
        if (answer.clubId.isBlank() || answer.userId.isBlank() || answer.taskId.isBlank())
            return ProcessResult.PERMANENT_FAILURE

        return when (item.operation.lowercase()) {
            "create", "update", "upsert" -> {
                val r = answerDataSource.upsertAnswer(answer)
                if (r.isSuccess) {
                    try { answerDao.clearPendingSync(answer.id) } catch (_: Exception) {}
                    ProcessResult.SUCCESS
                } else classifyError(r.exceptionOrNull())
            }
            else -> ProcessResult.PERMANENT_FAILURE
        }
    }

'''

    text = text.replace(classify_anchor, new_funcs + classify_anchor, 1)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ SyncWorker.kt пропатчен (бэкап: {backup.name})")
    return True


# ============================================================
# ПАТЧ 3: RequirementMaterialDao.kt — убрать OR clubId = ''
# ============================================================
def patch_material_dao() -> bool:
    print("\n🔧 Патчу RequirementMaterialDao.kt...")
    f = PKG / "data/local/dao/RequirementMaterialDao.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "OR clubId = ''" not in text:
        print("⚠️  Уже пропатчен")
        return True

    old = """WHERE requirementId = :requirementId
          AND (clubId = :clubId OR clubId = '')
        ORDER BY orderIndex ASC"""
    new = """WHERE requirementId = :requirementId
          AND clubId = :clubId
        ORDER BY orderIndex ASC"""
    text = text.replace(old, new)

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementMaterialDao.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 4: RequirementTaskDao.kt — убрать OR clubId = ''
# ============================================================
def patch_task_dao() -> bool:
    print("\n🔧 Патчу RequirementTaskDao.kt...")
    f = PKG / "data/local/dao/RequirementTaskDao.kt"
    if not f.exists():
        print(f"❌ Не найден {f}")
        return False

    text = f.read_text(encoding="utf-8")
    if "OR clubId = ''" not in text:
        print("⚠️  Уже пропатчен")
        return True

    # Два места
    text = text.replace(
        """WHERE requirementId = :requirementId
          AND (clubId = :clubId OR clubId = '')
        ORDER BY orderIndex ASC""",
        """WHERE requirementId = :requirementId
          AND clubId = :clubId
        ORDER BY orderIndex ASC"""
    )
    text = text.replace(
        """WHERE requirementId = :requirementId
          AND (clubId = :clubId OR clubId = '')
          AND isRequired = 1""",
        """WHERE requirementId = :requirementId
          AND clubId = :clubId
          AND isRequired = 1"""
    )

    backup = f.with_suffix(".kt.bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ RequirementTaskDao.kt пропатчен")
    return True


# ============================================================
# ПАТЧ 5: firestore.rules — секция requirements
# ============================================================
def patch_firestore_rules() -> bool:
    print("\n🔧 Патчу firestore.rules...")
    f = ROOT / "firestore.rules"
    if not f.exists():
        print(f"❌ Не найден {f} (в корне проекта)")
        return False

    text = f.read_text(encoding="utf-8")
    if "requirementTaskAnswers" in text:
        print("⚠️  Уже пропатчен")
        return True

    # Вставляем секцию requirements внутрь clubs/{clubId} match,
    # перед reports/{reportId}.
    anchor = "      match /reports/{reportId} {"
    if anchor not in text:
        print(f"❌ Не найден якорь reports в clubs")
        return False

    new_section = '''      // ============================================================
      // REQUIREMENTS — материалы и задания требования (внутри клуба)
      // ============================================================
      match /requirements/{requirementId} {
        // Материалы и задания видны всем участникам клуба.
        match /materials/{materialId} {
          allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
          allow create, update, delete: if isSignedIn() && (
            isConferenceAdmin()
            || (isDirector() && isOwnClub(clubId))
          );
          // Наставник может только флаг поставить (обновление ограниченного набора полей).
          allow update: if isSignedIn() && isClubStaff() && isOwnClub(clubId)
            && request.resource.data.diff(resource.data).affectedKeys()
                .hasOnly(['flaggedForEdit', 'flaggedBy', 'flaggedAt', 'flagComment']);
        }

        match /tasks/{taskId} {
          allow read: if isSignedIn() && (isOwnClub(clubId) || isConferenceAdmin());
          allow create, update, delete: if isSignedIn() && (
            isConferenceAdmin()
            || (isDirector() && isOwnClub(clubId))
          );
          allow update: if isSignedIn() && isClubStaff() && isOwnClub(clubId)
            && request.resource.data.diff(resource.data).affectedKeys()
                .hasOnly(['flaggedForEdit', 'flaggedBy', 'flaggedAt', 'flagComment']);
        }
      }

      // ============================================================
      // ANSWERS — ответы на проверочные задания (плоско, по клубу)
      // ============================================================
      match /requirementTaskAnswers/{answerId} {
        // Следопыт читает свои ответы.
        allow read: if isSignedIn() && (
          resource.data.userId == uid()
          || isConferenceAdmin()
          || (isClubStaff() && isOwnClub(clubId))
        );

        // Следопыт создаёт/обновляет только свой ответ, с userId == uid().
        allow create: if isSignedIn()
          && request.resource.data.userId == uid()
          && request.resource.data.clubId == clubId;

        // Обновление: автор может менять свой ответ (draft/submit).
        // Наставник/директор — менять статус, reviewedBy, reviewedAt, reviewComment.
        allow update: if isSignedIn() && (
          (resource.data.userId == uid()
            && request.resource.data.userId == uid())
          || (isClubStaff() && isOwnClub(clubId))
          || isConferenceAdmin()
        );

        allow delete: if isConferenceAdmin();
      }

'''

    text = text.replace(anchor, new_section + anchor, 1)

    backup = f.with_suffix(".bak")
    shutil.copy2(f, backup)
    f.write_text(text, encoding="utf-8")
    print(f"✅ firestore.rules пропатчен (бэкап: {backup.name})")
    return True


# ============================================================
# MAIN
# ============================================================
def main() -> None:
    print("=" * 60)
    print("  Pathfinder Hub — Сессия A2")
    print("=" * 60)

    write_files()

    results = {
        "RepositoryModule.kt": patch_repository_module(),
        "SyncWorker.kt":       patch_sync_worker(),
        "MaterialDao.kt":      patch_material_dao(),
        "TaskDao.kt":          patch_task_dao(),
        "firestore.rules":     patch_firestore_rules(),
    }

    print("\n" + "=" * 60)
    all_ok = all(results.values())
    for name, ok in results.items():
        mark = "✅" if ok else "❌"
        print(f"  {mark} {name}")

    if all_ok:
        print("\n✅ Все патчи применены.")
        print("\nСборка: ./gradlew assembleDebug")
        print("\nПроверь после сборки:")
        print("  1. RepositoryModule — 2 новых @Provides")
        print("  2. SyncWorker — 3 новые ветки entityType")
        print("  3. firestore.rules — секции requirements/* и requirementTaskAnswers/*")
        print("  4. Firebase Console → deploy rules")
    else:
        print("\n⚠️  Часть патчей не удалась. Смотри *.bak рядом с файлами.")

    print("=" * 60)


if __name__ == "__main__":
    main()