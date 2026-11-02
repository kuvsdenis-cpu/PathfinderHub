package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.BibleDao
import com.pathfinder.hub.data.local.entity.bible.BibleBookEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVerseEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVersionEntity
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleRepository @Inject constructor(
    private val bibleDao: BibleDao,
    private val syncQueue: SyncQueueManager,
) {
    companion object {
        const val DEFAULT_VERSION = "rst"
    }

    fun observeVersions(): Flow<List<BibleVersionEntity>> = bibleDao.observeVersions()
    fun observeBooks(versionId: String = DEFAULT_VERSION): Flow<List<BibleBookEntity>> =
        bibleDao.observeBooks(versionId)

    fun observeVerses(versionId: String, bookId: String, chapter: Int): Flow<List<BibleVerseEntity>> =
        bibleDao.observeVerses(versionId, bookId, chapter)

    suspend fun getBook(id: String): BibleBookEntity? = bibleDao.getBook(id)
    suspend fun getVerse(id: String): BibleVerseEntity? = bibleDao.getVerse(id)
    suspend fun getVerseCount(versionId: String): Int = bibleDao.getVerseCount(versionId)

    // ---------- Bookmarks (B2) ----------
    fun observeBookmarks(userId: String): Flow<List<BibleBookmarkEntity>> =
        bibleDao.observeBookmarks(userId)

    fun observeBookmark(userId: String, verseId: String): Flow<BibleBookmarkEntity?> =
        bibleDao.observeBookmark(userId, verseId)

    fun observeBookmarkedVerseIds(
        userId: String, bookId: String, chapter: Int
    ): Flow<List<String>> = bibleDao.observeBookmarkedVerseIds(userId, bookId, chapter)

    suspend fun getBookmark(userId: String, verseId: String): BibleBookmarkEntity? =
        bibleDao.getBookmark(userId, verseId)

    // ---------- B6.1: Заметки ----------
    suspend fun updateNote(userId: String, verseId: String, note: String?) {
        val existing = bibleDao.getBookmark(userId, verseId) ?: return
        val updated = existing.copy(note = note?.takeIf { it.isNotBlank() }, pendingSync = true)
        bibleDao.upsertBookmark(updated)
        syncQueue.enqueue(
            entityType = "bible_bookmark",
            entityId = updated.verseId,
            operation = "upsert",
            payload = updated,
        )
    }

    // ---------- B6.2: Поиск ----------
    suspend fun searchVerses(query: String, limit: Int = 100): List<BibleVerseEntity> =
        bibleDao.searchVerses(DEFAULT_VERSION, query, limit)

    // ---------- B6.3: Счётчики ----------
    fun observeBookmarkCountsByBook(userId: String): Flow<List<com.pathfinder.hub.data.local.dao.BookmarkCount>> =
        bibleDao.observeBookmarkCountsByBook(userId)

    suspend fun isBookmarked(userId: String, verseId: String): Boolean =
        bibleDao.isBookmarked(userId, verseId) > 0

    suspend fun upsertBookmark(b: BibleBookmarkEntity) {
        bibleDao.upsertBookmark(b)
        syncQueue.enqueue(
            entityType = "bible_bookmark",
            entityId = b.verseId,
            operation = "upsert",
            payload = b,
        )
    }

    suspend fun deleteBookmark(userId: String, verseId: String) {
        val existing = bibleDao.getBookmark(userId, verseId)
        bibleDao.deleteBookmark(userId, verseId)
        if (existing != null) {
            syncQueue.enqueue(
                entityType = "bible_bookmark",
                entityId = verseId,
                operation = "delete",
                payload = existing,
            )
        }
    }
}
