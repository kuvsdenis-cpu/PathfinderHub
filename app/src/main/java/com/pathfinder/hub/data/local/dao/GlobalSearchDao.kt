package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.info.InfoArticleEntity
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity

@Dao
interface GlobalSearchDao {

    @Query("""
        SELECT * FROM users
        WHERE firstName LIKE :pattern OR lastName LIKE :pattern OR email LIKE :pattern
        ORDER BY firstName, lastName
        LIMIT 50
    """)
    suspend fun searchUsers(pattern: String): List<UserEntity>

    @Query("""
        SELECT * FROM events
        WHERE title LIKE :pattern OR description LIKE :pattern
        ORDER BY dateStart DESC
        LIMIT 50
    """)
    suspend fun searchEvents(pattern: String): List<EventEntity>

    @Query("""
        SELECT * FROM honors
        WHERE name LIKE :pattern OR description LIKE :pattern
        ORDER BY name
        LIMIT 50
    """)
    suspend fun searchHonors(pattern: String): List<HonorEntity>

    @Query("""
        SELECT * FROM books
        WHERE title LIKE :pattern OR author LIKE :pattern
        ORDER BY title
        LIMIT 50
    """)
    suspend fun searchBooks(pattern: String): List<BookEntity>

    @Query("""
        SELECT * FROM tasks
        WHERE title LIKE :pattern OR description LIKE :pattern
        ORDER BY createdAt DESC
        LIMIT 50
    """)
    suspend fun searchTasks(pattern: String): List<TaskEntity>

    @Query("""
        SELECT * FROM info_articles
        WHERE title LIKE :pattern OR content LIKE :pattern
        ORDER BY title
        LIMIT 50
    """)
    suspend fun searchInfoArticles(pattern: String): List<InfoArticleEntity>
}
