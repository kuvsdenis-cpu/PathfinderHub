package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pathfinder.hub.data.local.entity.bible.BibleBookEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVerseEntity
import com.pathfinder.hub.data.local.entity.bible.BibleVersionEntity
import com.pathfinder.hub.data.local.entity.bible.BibleBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleDao {

    // ---------- Version ----------
    @Query("SELECT * FROM bible_versions ORDER BY createdAt")
    fun observeVersions(): Flow<List<BibleVersionEntity>>

    @Query("SELECT * FROM bible_versions WHERE id = :id")
    suspend fun getVersion(id: String): BibleVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(v: BibleVersionEntity)

    @Query("DELETE FROM bible_versions WHERE id = :versionId")
    suspend fun deleteVersion(versionId: String)

    // ---------- Book ----------
    @Query("SELECT * FROM bible_books WHERE versionId = :versionId ORDER BY `order`")
    fun observeBooks(versionId: String): Flow<List<BibleBookEntity>>

    @Query("SELECT * FROM bible_books WHERE versionId = :versionId ORDER BY `order`")
    suspend fun getBooks(versionId: String): List<BibleBookEntity>

    @Query("SELECT * FROM bible_books WHERE id = :id")
    suspend fun getBook(id: String): BibleBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(books: List<BibleBookEntity>)

    // ---------- Verse ----------
    @Query("""
        SELECT * FROM bible_verses
        WHERE versionId = :versionId AND bookId = :bookId AND chapter = :chapter
        ORDER BY verse
    """)
    fun observeVerses(versionId: String, bookId: String, chapter: Int): Flow<List<BibleVerseEntity>>

    @Query("""
        SELECT * FROM bible_verses
        WHERE versionId = :versionId AND bookId = :bookId AND chapter = :chapter
        ORDER BY verse
    """)
    suspend fun getVerses(versionId: String, bookId: String, chapter: Int): List<BibleVerseEntity>

    @Query("SELECT * FROM bible_verses WHERE id = :id")
    suspend fun getVerse(id: String): BibleVerseEntity?

    @Query("SELECT COUNT(*) FROM bible_verses WHERE versionId = :versionId")
    suspend fun getVerseCount(versionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersesBatch(verses: List<BibleVerseEntity>)

    @Transaction
    suspend fun seedVersesInBatches(verses: List<BibleVerseEntity>, batchSize: Int = 500) {
        verses.chunked(batchSize).forEach { insertVersesBatch(it) }
    }

    // ---------- Bookmarks (B2) ----------
    @Query("SELECT * FROM bible_bookmarks WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeBookmarks(userId: String): Flow<List<BibleBookmarkEntity>>

    @Query("SELECT * FROM bible_bookmarks WHERE userId = :userId AND verseId = :verseId LIMIT 1")
    fun observeBookmark(userId: String, verseId: String): Flow<BibleBookmarkEntity?>

    @Query("""
        SELECT verseId FROM bible_bookmarks
        WHERE userId = :userId AND bookId = :bookId AND chapter = :chapter
    """)
    fun observeBookmarkedVerseIds(userId: String, bookId: String, chapter: Int): Flow<List<String>>

    @Query("SELECT * FROM bible_bookmarks WHERE userId = :userId AND verseId = :verseId LIMIT 1")
    suspend fun getBookmark(userId: String, verseId: String): BibleBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(b: BibleBookmarkEntity)

    @Query("DELETE FROM bible_bookmarks WHERE userId = :userId AND verseId = :verseId")
    suspend fun deleteBookmark(userId: String, verseId: String)

    @Query("SELECT COUNT(*) FROM bible_bookmarks WHERE userId = :userId AND verseId = :verseId")
    suspend fun isBookmarked(userId: String, verseId: String): Int

    // ---------- B6.2: Поиск ----------
    @Query("""
        SELECT * FROM bible_verses
        WHERE versionId = :versionId
          AND LOWER(text) LIKE '%' || LOWER(:query) || '%'
        ORDER BY bookId, chapter, verse
        LIMIT :limit
    """)
    suspend fun searchVerses(
        versionId: String, query: String, limit: Int = 100
    ): List<BibleVerseEntity>

    // ---------- B6.3: Счётчики ----------
    @Query("""
        SELECT bookId, COUNT(*) as cnt FROM bible_bookmarks
        WHERE userId = :userId
        GROUP BY bookId
    """)
    fun observeBookmarkCountsByBook(userId: String): Flow<List<BookmarkCount>>
}

