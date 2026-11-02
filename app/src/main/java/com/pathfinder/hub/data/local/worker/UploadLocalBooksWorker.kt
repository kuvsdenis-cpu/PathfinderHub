package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pathfinder.hub.data.repository.BookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UploadLocalBooksWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bookRepository: BookRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val m = bookRepository.findMigratableBooks()
        Log.d(TAG, "Найдено: ${m.size}")
        var ok = 0
        for (b in m) {
            try { if (bookRepository.migrateLocalBookToCloud(b)) ok++ }
            catch (e: Exception) { Log.e(TAG, "fail ${b.id}", e) }
        }
        Log.d(TAG, "Мигрировано: $ok / ${m.size}")
        Result.success()
    } catch (e: Exception) { Log.e(TAG, "worker fail", e); Result.failure() }
    companion object { const val TAG = "UploadLocalBooks"; const val WORK_NAME = "upload_local_books" }
}
