package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.newspaper.*
import com.pathfinder.hub.data.local.relation.NewspaperWithModeration
import kotlinx.coroutines.flow.Flow

@Dao
interface NewspaperDao {
    @Query("SELECT * FROM newspaper_issues ORDER BY year DESC, number DESC")
    fun observeAll(): Flow<List<NewspaperIssueEntity>>
    @Query("""SELECT * FROM newspaper_issues WHERE isHistorical = :historical
        ORDER BY year DESC, number DESC""")
    fun observeByType(historical: Boolean): Flow<List<NewspaperIssueEntity>>
    @Query("SELECT * FROM newspaper_issues WHERE year = :year ORDER BY number DESC")
    fun observeByYear(year: Int): Flow<List<NewspaperIssueEntity>>
    @Query("SELECT * FROM newspaper_issues WHERE id = :id")
    suspend fun getIssue(id: String): NewspaperIssueEntity?
    @Transaction
    @Query("SELECT * FROM newspaper_issues WHERE id = :id")
    fun observeIssueWithModeration(id: String): Flow<NewspaperWithModeration?>
    @Query("""SELECT * FROM newspaper_issues WHERE title LIKE '%' || :query || '%'
        OR number = :number ORDER BY year DESC""")
    fun search(query: String, number: Int): Flow<List<NewspaperIssueEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(issue: NewspaperIssueEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(issues: List<NewspaperIssueEntity>)
    @Query("UPDATE newspaper_issues SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
    @Delete suspend fun delete(issue: NewspaperIssueEntity)
    @Query("SELECT * FROM newspaper_templates WHERE level = :level")
    fun observeTemplates(level: String): Flow<List<NewspaperTemplateEntity>>
    @Query("SELECT * FROM newspaper_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): NewspaperTemplateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(t: NewspaperTemplateEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModeration(m: NewspaperModerationEntity)
    @Query("SELECT * FROM newspaper_moderations WHERE status = 'pending'")
    fun observePendingModerations(): Flow<List<NewspaperModerationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPublication(p: NewspaperPublicationEntity)
    @Query("SELECT * FROM cached_newspapers")
    fun observeCached(): Flow<List<CachedNewspaperEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCached(c: CachedNewspaperEntity)
    @Query("DELETE FROM cached_newspapers WHERE issueId = :id")
    suspend fun deleteCached(id: String)
    @Query("SELECT SUM(fileSizeBytes) FROM cached_newspapers")
    suspend fun getTotalCacheSize(): Long?
    @Query("SELECT * FROM cached_newspapers ORDER BY downloadedAt ASC LIMIT :limit")
    suspend fun getOldestCached(limit: Int): List<CachedNewspaperEntity>
}
