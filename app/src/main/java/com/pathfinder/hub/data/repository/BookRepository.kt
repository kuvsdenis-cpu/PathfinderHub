package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.BookDao
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.remote.FirestoreBookDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import com.pathfinder.hub.data.local.dao.UserClubDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val bookDao: BookDao,
    private val userClubDao: UserClubDao,
    private val syncQueueManager: SyncQueueManager,
    private val storageRepo: StorageRepository,
    private val firestoreBookDS: FirestoreBookDataSource,
) {
    fun observeAll(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeAvailable(clubId: String?): Flow<List<BookEntity>> =
        bookDao.observeAvailable(clubId)

    fun observeById(id: String): Flow<BookEntity?> = bookDao.observeById(id)

    suspend fun getById(id: String): BookEntity? = bookDao.getById(id)

    suspend fun getByIds(ids: List<String>): List<BookEntity> =
        if (ids.isEmpty()) emptyList() else bookDao.getByIds(ids)

    suspend fun upsert(book: BookEntity) = bookDao.upsert(book)

    suspend fun upsertAll(books: List<BookEntity>) = bookDao.upsertAll(books)

    suspend fun deleteById(id: String) = bookDao.deleteById(id)

    suspend fun countGlobal(): Int = bookDao.countGlobal()

    suspend fun pushClubBook(book: BookEntity) {
        if (book.clubId == null) return
        syncQueueManager.enqueue(
            entityType = "book",
            entityId = book.id,
            operation = "upsert",
            payload = book,
            priority = "high",
        )
    }

    suspend fun saveBookFromFile(
        uri: android.net.Uri,
        title: String,
        author: String,
        description: String?,
        coverUrl: String?,
        clubId: String,
        uploadedBy: String,
    ): BookEntity {
        val context = appContext
        val mimeType = context.contentResolver.getType(uri)
        val displayName = queryDisplayName(context, uri)
        val parsed = com.pathfinder.hub.data.books.BookFileParser.parse(context, uri, mimeType, displayName)
        val id = java.util.UUID.randomUUID().toString()
        val textFile = java.io.File(context.filesDir, "books/$id.txt")
        textFile.parentFile?.mkdirs()
        textFile.writeText(parsed.plainText)
        val contentUrl = try {
            storageRepo.uploadBookText(clubId, id, title, parsed.plainText, appContext.cacheDir)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "uploadBookText failed for $id", e)
            null
        }
        val entity = BookEntity(
            id = id, title = title, author = author, level = 0,
            description = description, externalUrl = null,
            contentPath = "books/$id.txt", contentUrl = contentUrl,
            contentPreview = parsed.plainText.take(1000), coverUrl = coverUrl,
            clubId = clubId, createdBy = uploadedBy,
            createdAt = System.currentTimeMillis(), pendingSync = true,
        )
        bookDao.upsert(entity)
        pushClubBook(entity)
        return entity
    }

    private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) { null }

    /**
     * Гарантировать contentUrl:
     *   - если null и есть clubId + storageRepo -> сгенерировать из ключа
     *   - если есть -> refresh presigned
     *   - сохранить в Room
     */
    suspend fun ensureContentUrl(book: BookEntity): BookEntity {
        val cid = book.clubId ?: return book
        val key = "clubs/$cid/books/${book.id}.txt"
        val fresh = try {
            storageRepo.generatePresignedUrl(key)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "ensureContentUrl presign failed: $key", e)
            return book
        }
        if (fresh == book.contentUrl) return book
        val updated = book.copy(contentUrl = fresh)
        bookDao.upsert(updated)
        return updated
    }

    /** Перегенерировать presigned contentUrl (TTL 365 дней) и сохранить. */
    suspend fun refreshContentUrl(book: BookEntity): BookEntity {
        val url = book.contentUrl ?: return book
        val fresh = storageRepo.refreshPresignedUrl(url) ?: return book
        if (fresh == url) return book
        val updated = book.copy(contentUrl = fresh)
        bookDao.upsert(updated)
        return updated
    }

    /** Книги, у которых есть локальный файл в filesDir и ещё нет contentUrl. */
    suspend fun findMigratableBooks(): List<BookEntity> {
        return try {
            bookDao.observeAll().first().filter { b ->
                b.deletedAt == null &&
                    b.clubId != null &&
                    b.contentUrl == null &&
                    !b.contentPath.isNullOrBlank() &&
                    java.io.File(appContext.filesDir, b.contentPath!!).exists()
            }
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "findMigratableBooks failed", e)
            emptyList()
        }
    }
    /** Книги без локального файла и без contentUrl — «битые». */
    suspend fun findBrokenBooks(): List<BookEntity> {
        return try {
            bookDao.observeAll().first().filter { b ->
                b.deletedAt == null &&
                    b.clubId != null && b.contentUrl == null &&
                    (b.contentPath.isNullOrBlank() ||
                        !java.io.File(appContext.filesDir, b.contentPath!!).exists())
            }
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "findBrokenBooks failed", e)
            emptyList()
        }
    }
    /** Прочитать локальный файл → upload → сохранить contentUrl → push в Firestore. */
    suspend fun migrateLocalBookToCloud(book: BookEntity): Boolean {
        val cid = book.clubId ?: return false
        val path = book.contentPath ?: return false
        val file = java.io.File(appContext.filesDir, path)
        if (!file.exists()) return false
        val text = try { file.readText() } catch (_: Exception) { return false }
        val url = storageRepo.uploadBookText(
            cid, book.id, book.title, text, appContext.cacheDir,
        ) ?: return false
        val updated = book.copy(contentUrl = url, pendingSync = true)
        bookDao.upsert(updated)
        pushClubBook(updated)
        return true
    }
    /**
     * Удалить книгу: soft delete локально → Firestore → hard delete (если online).
     * Если Firestore недоступен — soft-deleted книга остаётся, очередь на удаление.
     * pullClubBooks пропускает книги с deletedAt != null → не возвращает.
     */
    suspend fun deleteClubBook(book: BookEntity) {
        // 1. Soft delete локально
        val soft = book.copy(deletedAt = java.util.Date(), pendingSync = true)
        bookDao.upsert(soft)

        val cid = book.clubId ?: return
        val r = try {
            firestoreBookDS.deleteBook(cid, book.id)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "deleteClubBook firestore failed ${book.id}", e)
            Result.failure(e)
        }
        if (r.isSuccess) {
            // 2. Online — hard delete локально
            bookDao.deleteById(book.id)
            android.util.Log.d("BookRepository", "deleteClubBook hard-deleted: ${book.id}")
        } else {
            // 3. Offline — очередь, SyncWorker удалит позже
            try {
                syncQueueManager.enqueue(
                    entityType = "book",
                    entityId = book.id,
                    operation = "delete",
                    payload = book,
                    priority = "high",
                )
                android.util.Log.d("BookRepository", "deleteClubBook queued: ${book.id}")
            } catch (_: Exception) {}
        }
    }

    /** Удалить все «битые» книги (локально + Firestore). Возвращает количество. */
    suspend fun deleteBrokenBooks(): Int {
        val broken = findBrokenBooks()
        broken.forEach {
            try { deleteClubBook(it) } catch (_: Exception) {}
        }
        return broken.size
    }
    /** Presigned для старого ключа {uuid}.txt — для уже загруженных книг. */
    suspend fun legacyContentUrl(book: BookEntity): String? {
        val cid = book.clubId ?: return null
        val key = storageRepo.buildLegacyBookKey(cid, book.id)
        return try {
            storageRepo.generatePresignedUrl(key)
        } catch (e: Exception) {
            android.util.Log.w("BookRepository", "legacyContentUrl failed: $key", e)
            null
        }
    }

    suspend fun getClubIdsForUser(userId: String): List<String> {
        return try {
            userClubDao.getClubIds(userId)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
