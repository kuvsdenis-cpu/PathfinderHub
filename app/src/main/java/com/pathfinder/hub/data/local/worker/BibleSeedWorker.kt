package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.pathfinder.hub.data.bible.BibleBookNames
import com.pathfinder.hub.data.local.dao.BibleDao
import com.pathfinder.hub.data.local.entity.bible.BibleBookEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVerseEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVersionEntity
import com.pathfinder.hub.data.local.entity.learning.ContentVersionEntity
import com.pathfinder.hub.data.repository.HonorRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date

/**
 * Засев Библии из assets/bible/rst.json (NDJSON, одна строка = один стих).
 *
 * Двухпроходный засев:
 *   PASS 1  книги (bible_books), чтобы FK bible_verses.bookId -> bible_books.id был удовлетворён;
 *   PASS 2  стихи батчами по 500 (без OOM).
 *
 * Идемпотентен: если версия совпадает и книги+стихи уже есть  skip.
 */
@HiltWorker
class BibleSeedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val bibleDao: BibleDao,
    private val honorRepository: HonorRepository
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        return try {
            val versionId = VERSION_ID
            val currentVersion = honorRepository.getLatestContentVersion()
            val existingBookCount = bibleDao.getBooks(versionId).size
            val existingVerseCount = bibleDao.getVerseCount(versionId)
            Log.d(TAG, "STATE on start: books=$existingBookCount, verses=$existingVerseCount, " +
                    "version=$VERSION_KEY, current=${currentVersion?.version}")
            val alreadySeeded = existingVerseCount > 0

            if (alreadySeeded && existingBookCount > 0 && currentVersion?.version == VERSION_KEY) {
                Log.d(TAG, " Библия уже засеяна (books=$existingBookCount, verses=$existingVerseCount), skip")
                return Result.success()
            }

            Log.d(TAG, " Засев Библии ($VERSION_KEY)...")

            if (alreadySeeded) {
                Log.d(TAG, " Удаляем старые данные версии $versionId...")
                bibleDao.deleteVersion(versionId)
            }

            // 1. Version
            bibleDao.upsertVersion(
                BibleVersionEntity(
                    id = versionId,
                    name = "Синодальный перевод",
                    language = "ru",
                    copyright = "Public Domain",
                    createdAt = System.currentTimeMillis()
                )
            )

            // 2. PASS 1: собираем bookChapters (ID + главы)  без стихов в памяти
            val bookChapters = mutableMapOf<String, MutableSet<Int>>()
            applicationContext.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val raw = try {
                        gson.fromJson(line, RawVerse::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, " Skip bad JSON in PASS 1: ${line.take(120)}", e)
                        return@forEach
                    } ?: return@forEach

                    if (BibleBookNames.RU[raw.bookId] == null) {
                        Log.w(TAG, " Skip unknown book_id in PASS 1: ${raw.bookId}")
                        return@forEach
                    }
                    bookChapters.getOrPut(raw.bookId) { mutableSetOf() }.add(raw.chapter)
                }
            }
            Log.d(TAG, "PASS 1: книг = ${bookChapters.size}")

            // 3. КНИГИ СНАЧАЛА  FK bible_verses.bookId -> bible_books.id
            val books = bookChapters.mapNotNull { (bookId, chapters) ->
                val info = BibleBookNames.RU[bookId] ?: return@mapNotNull null
                BibleBookEntity(
                    id = "$versionId:$bookId",
                    versionId = versionId,
                    name = info.name,
                    shortName = info.shortName,
                    order = info.order,
                    chaptersCount = chapters.size
                )
            }.sortedBy { it.order }
            bibleDao.upsertBooks(books)
            Log.d(TAG, "книги вставлены: ${books.size}")

            // 4. PASS 2: стихи батчами по 500  FK уже удовлетворён
            var totalVerses = 0
            var batch = ArrayList<BibleVerseEntity>(BATCH_SIZE)
            applicationContext.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val raw = try {
                        gson.fromJson(line, RawVerse::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, " Skip bad JSON in PASS 2: ${line.take(120)}", e)
                        return@forEach
                    } ?: return@forEach

                    if (BibleBookNames.RU[raw.bookId] == null) return@forEach

                    batch.add(
                        BibleVerseEntity(
                            id = "$versionId:${raw.bookId}:${raw.chapter}:${raw.verse}",
                            versionId = versionId,
                            bookId = "$versionId:${raw.bookId}",
                            chapter = raw.chapter,
                            verse = raw.verse,
                            text = raw.text
                        )
                    )
                    totalVerses++

                    if (batch.size >= BATCH_SIZE) {
                        bibleDao.seedVersesInBatches(batch, batchSize = BATCH_SIZE)
                        Log.d(TAG, "  записано $totalVerses стихов")
                        batch = ArrayList(BATCH_SIZE)
                    }
                }
            }
            if (batch.isNotEmpty()) {
                bibleDao.seedVersesInBatches(batch, batchSize = BATCH_SIZE)
            }

            // 5. Помечаем версию
            honorRepository.upsertContentVersion(
                ContentVersionEntity(
                    version = VERSION_KEY,
                    updatedAt = Date(),
                    levels = 0,
                    honors = 0,
                    changelog = "Bible RST seed"
                )
            )

            Log.d(TAG, " Библия засеяна: $totalVerses стихов, ${books.size} книг")
            Result.success()
        } catch (e: java.io.IOException) {
            Log.e(TAG, " I/O ошибка засева Библии  retry", e)
            Result.retry()
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, " SQLite ошибка засева Библии  retry", e)
            Result.retry()
        } catch (t: Throwable) {
            Log.e(TAG, " Фатальная ошибка засева Библии  БЕЗ retry", t)
            Result.failure()
        }
    }

    private data class RawVerse(
        val book_id: String,
        val chapter: Int,
        val verse: Int,
        val text: String
    ) {
        val bookId: String get() = book_id
    }

    companion object {
        const val TAG = "BibleSeedWorker"
        const val WORK_NAME = "bible_seed"
        const val VERSION_ID = "rst"
        const val VERSION_KEY = "bible_rst_v1"
        const val ASSET_PATH = "bible/rst.json"
        private const val BATCH_SIZE = 500
    }
}