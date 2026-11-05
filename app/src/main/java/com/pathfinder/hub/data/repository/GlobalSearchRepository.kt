package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.GlobalSearchDao
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.local.entity.info.InfoArticleEntity
import com.pathfinder.hub.data.local.entity.learning.BookEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.planning.EventEntity
import com.pathfinder.hub.data.local.entity.planning.TaskEntity
import javax.inject.Inject

class GlobalSearchRepository @Inject constructor(
    private val dao: GlobalSearchDao,
) {

    suspend fun searchUsers(query: String): List<UserEntity> =
        if (query.isBlank()) emptyList()
        else dao.searchUsers("%${query.trim()}%")

    suspend fun searchEvents(query: String): List<EventEntity> =
        if (query.isBlank()) emptyList()
        else dao.searchEvents("%${query.trim()}%")

    suspend fun searchHonors(query: String): List<HonorEntity> =
        if (query.isBlank()) emptyList()
        else dao.searchHonors("%${query.trim()}%")

    suspend fun searchBooks(query: String): List<BookEntity> =
        if (query.isBlank()) emptyList()
        else dao.searchBooks("%${query.trim()}%")

    suspend fun searchTasks(query: String): List<TaskEntity> =
        if (query.isBlank()) emptyList()
        else dao.searchTasks("%${query.trim()}%")

    suspend fun searchInfoArticles(query: String): List<InfoArticleEntity> =
        if (query.isBlank()) emptyList()
        else dao.searchInfoArticles("%${query.trim()}%")
}
