package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.NewspaperDao
import com.pathfinder.hub.data.local.entity.newspaper.*
import com.pathfinder.hub.data.local.relation.NewspaperWithModeration
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NewspaperRepository @Inject constructor(private val dao: NewspaperDao) {
    fun observeAll(): Flow<List<NewspaperIssueEntity>> = dao.observeAll()
    fun observeByType(h: Boolean): Flow<List<NewspaperIssueEntity>> = dao.observeByType(h)
    fun observeByYear(y: Int): Flow<List<NewspaperIssueEntity>> = dao.observeByYear(y)
    suspend fun getIssue(id: String): NewspaperIssueEntity? = dao.getIssue(id)
    fun observeIssueWithModeration(id: String): Flow<NewspaperWithModeration?> = dao.observeIssueWithModeration(id)
    fun search(q: String, n: Int): Flow<List<NewspaperIssueEntity>> = dao.search(q, n)
    suspend fun upsert(i: NewspaperIssueEntity) = dao.upsert(i)
    suspend fun upsertAll(is_: List<NewspaperIssueEntity>) = dao.upsertAll(is_)
    suspend fun updateStatus(id: String, s: String) = dao.updateStatus(id, s)
    suspend fun delete(i: NewspaperIssueEntity) = dao.delete(i)
    fun observeTemplates(l: String): Flow<List<NewspaperTemplateEntity>> = dao.observeTemplates(l)
    suspend fun getDefaultTemplate(): NewspaperTemplateEntity? = dao.getDefaultTemplate()
    suspend fun upsertTemplate(t: NewspaperTemplateEntity) = dao.upsertTemplate(t)
    suspend fun upsertModeration(m: NewspaperModerationEntity) = dao.upsertModeration(m)
    fun observePendingModerations(): Flow<List<NewspaperModerationEntity>> = dao.observePendingModerations()
    suspend fun upsertPublication(p: NewspaperPublicationEntity) = dao.upsertPublication(p)
    fun observeCached(): Flow<List<CachedNewspaperEntity>> = dao.observeCached()
    suspend fun upsertCached(c: CachedNewspaperEntity) = dao.upsertCached(c)
    suspend fun deleteCached(id: String) = dao.deleteCached(id)
    suspend fun getTotalCacheSize(): Long? = dao.getTotalCacheSize()
    suspend fun getOldestCached(l: Int): List<CachedNewspaperEntity> = dao.getOldestCached(l)
}
