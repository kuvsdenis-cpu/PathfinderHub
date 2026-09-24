#!/usr/bin/env python3
"""
Генератор файлов синхронизации Room ↔ Firestore для Pathfinder Hub.
Создаёт 6 файлов + обновляет PathfinderApp.kt
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = []

def add(path, content):
    FILES.append((path, content.strip() + "\n"))

def write_all():
    for path, content in FILES:
        full = ROOT / path
        full.parent.mkdir(parents=True, exist_ok=True)
        with open(full, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"OK {path}")
    print(f"\nГотово. Создано файлов: {len(FILES)}")

# ============================================================
# 1. FirestoreSyncService.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/sync/FirestoreSyncService.kt",
    '''
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
''')

# ============================================================
# 2. SyncQueueManager.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/sync/SyncQueueManager.kt",
    '''
package com.pathfinder.hub.data.sync

import android.util.Log
import com.google.gson.Gson
import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.dao.SyncDao
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import com.pathfinder.hub.data.local.entity.offline.SyncStatusEntity
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncQueueManager @Inject constructor(
    private val syncDao: SyncDao,
    private val sessionManager: SessionManager
) {

    private val gson = Gson()

    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: String,
        payload: Any,
        priority: String = "normal"
    ) {
        val userId = sessionManager.getUserId() ?: return
        val item = SyncQueueItemEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            operation = operation,
            entityType = entityType,
            entityId = entityId,
            payload = gson.toJson(payload),
            status = "pending",
            retryCount = 0,
            lastAttemptAt = null,
            errorMessage = null,
            createdAt = Date(),
            priority = priority
        )
        syncDao.upsertItem(item)
        Log.d(TAG, "Enqueue: $operation $entityType/$entityId")
    }

    suspend fun getNextBatch(limit: Int = 50): List<SyncQueueItemEntity> {
        return syncDao.getNextBatch(limit)
    }

    suspend fun updateStatus(
        id: String,
        status: String,
        retryCount: Int = 0,
        errorMessage: String? = null
    ) {
        syncDao.updateItemStatus(
            id = id,
            status = status,
            retry = retryCount,
            at = System.currentTimeMillis(),
            err = errorMessage
        )
    }

    suspend fun remove(id: String) {
        syncDao.deleteItem(id)
    }

    suspend fun updateSyncStatus(
        isOnline: Boolean = true,
        isSyncing: Boolean = false,
        queueSize: Int = 0,
        lastError: String? = null
    ) {
        syncDao.upsertStatus(
            SyncStatusEntity(
                id = 1,
                isOnline = isOnline,
                isSyncing = isSyncing,
                queueSize = queueSize,
                lastSyncAt = if (isSyncing) null else Date(),
                lastError = lastError,
                conflictsCount = 0
            )
        )
    }

    companion object {
        private const val TAG = "SyncQueue"
    }
}
''')

# ============================================================
# 3. SyncWorker.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/sync/SyncWorker.kt",
    '''
package com.pathfinder.hub.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.pathfinder.hub.data.local.entity.offline.SyncQueueItemEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueue: SyncQueueManager,
    private val firestore: FirestoreSyncService
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "Start sync...")
        val batch = syncQueue.getNextBatch(50)
        if (batch.isEmpty()) {
            Log.d(TAG, "Queue empty")
            return Result.success()
        }

        syncQueue.updateSyncStatus(isSyncing = true, queueSize = batch.size)

        var success = 0
        var errors = 0
        for (item in batch) {
            if (processItem(item)) {
                syncQueue.remove(item.id)
                success++
            } else {
                errors++
                if (item.retryCount >= 5) {
                    syncQueue.updateStatus(
                        id = item.id,
                        status = "failed",
                        retryCount = item.retryCount,
                        errorMessage = "Max retries"
                    )
                }
            }
        }

        syncQueue.updateSyncStatus(
            isSyncing = false,
            queueSize = 0,
            lastError = if (errors > 0) "Errors: $errors" else null
        )
        Log.d(TAG, "Sync done: success=$success, errors=$errors")
        return Result.success()
    }

    private suspend fun processItem(item: SyncQueueItemEntity): Boolean {
        return try {
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(item.payload, Map::class.java) as? Map<String, Any?>
                ?: emptyMap()

            val collectionPath = when (item.entityType) {
                "user" -> "users"
                "club" -> "clubs"
                "event" -> "clubs/${data["clubId"]}/events"
                "task" -> "clubs/${data["clubId"]}/tasks"
                "level_progress" -> "users/${item.userId}/levelProgress"
                "honor_progress" -> "users/${item.userId}/honorProgress"
                else -> return false
            }

            val result = when (item.operation) {
                "create", "update" -> firestore.pushDocument(
                    collection = collectionPath,
                    documentId = item.entityId,
                    data = data
                )
                "delete" -> firestore.deleteDocument(
                    collection = collectionPath,
                    documentId = item.entityId
                )
                else -> Result.failure(Exception("Unknown op: ${item.operation}"))
            }

            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Process error ${item.id}", e)
            syncQueue.updateStatus(
                id = item.id,
                status = "pending",
                retryCount = item.retryCount + 1,
                errorMessage = e.message
            )
            false
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "firestore_sync"
    }
}
''')

# ============================================================
# 4. SyncScheduler.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/data/sync/SyncScheduler.kt",
    '''
package com.pathfinder.hub.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
''')

# ============================================================
# 5. SyncViewModel.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/sync/SyncViewModel.kt",
    '''
package com.pathfinder.hub.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.dao.SyncDao
import com.pathfinder.hub.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isSyncing: Boolean = false,
    val queueSize: Int = 0,
    val lastError: String? = null
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncDao: SyncDao
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            syncDao.observeStatus().collect { status ->
                status?.let {
                    _state.update { s ->
                        s.copy(
                            isSyncing = it.isSyncing,
                            queueSize = it.queueSize,
                            lastError = it.lastError
                        )
                    }
                }
            }
        }
    }

    fun syncNow() {
        syncScheduler.syncNow()
    }
}
''')

# ============================================================
# 6. SyncScreen.kt
# ============================================================
add(
    "app/src/main/java/com/pathfinder/hub/ui/screens/sync/SyncScreen.kt",
    '''
package com.pathfinder.hub.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pathfinder.hub.ui.theme.PathfinderBlue

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Синхронизация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PathfinderBlue,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            if (state.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = PathfinderBlue
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = PathfinderBlue,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (state.isSyncing) "Синхронизация…" else "Готово",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "В очереди: ${state.queueSize}",
                style = MaterialTheme.typography.bodyLarge
            )

            state.lastError?.let { error ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = viewModel::syncNow,
                enabled = !state.isSyncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Синхронизировать сейчас")
            }
        }
    }
}
''')

# ============================================================
# ЗАПУСК
# ============================================================

if __name__ == "__main__":
    write_all()
    print("\nСледующие шаги:")
    print("1. Добавьте в app/build.gradle.kts:")
    print('   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")')
    print("2. Обновите PathfinderApp.kt: добавьте @Inject lateinit var syncScheduler: SyncScheduler")
    print("   и в onCreate вызовите syncScheduler.schedulePeriodic()")
    print("3. Соберите: .\\gradlew clean assembleDebug")