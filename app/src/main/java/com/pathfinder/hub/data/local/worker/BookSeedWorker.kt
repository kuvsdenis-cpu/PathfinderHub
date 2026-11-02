package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.repository.BookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Seed книг из assets/books/books.json.
 * Идемпотентно: если в таблице уже есть глобальные книги (clubId = NULL),
 * повторно не заливает.
 */
@HiltWorker
class BookSeedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bookRepository: BookRepository,
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "🚀 Seed книг из assets/books/books.json")
        return try {
            val json = readAsset("books/books.json")
            if (json == null) {
                Log.w(TAG, "⚠️ assets/books/books.json не найден")
                return Result.failure()
            }

            val type = object : TypeToken<List<BookEntity>>() {}.type
            val books: List<BookEntity> = gson.fromJson(json, type)
            if (books.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ books.json пуст")
                return Result.success()
            }

            val existing = bookRepository.countGlobal()
            if (existing > 0) {
                Log.d(TAG, "✓ Глобальные книги уже в Room: $existing — пропускаем")
                return Result.success()
            }

            bookRepository.upsertAll(books)
            Log.d(TAG, "✅ Загружено книг: ${books.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка seed книг", e)
            Result.failure()
        }
    }

    private fun readAsset(name: String): String? = try {
        applicationContext.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.w(TAG, "Asset не найден: $name", e)
        null
    }

    companion object {
        const val TAG = "BookSeedWorker"
        const val WORK_NAME = "seed_books"
    }
}
