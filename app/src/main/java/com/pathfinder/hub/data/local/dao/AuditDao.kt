package com.pathfinder.hub.data.local.dao
import androidx.room.*
import com.pathfinder.hub.data.local.entity.audit.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Query("""SELECT * FROM audit_log WHERE clubId = :clubId
        ORDER BY timestamp DESC LIMIT :limit""")
    fun observeClubAudit(clubId: String, limit: Int = 200): Flow<List<AuditLogEntity>>
    @Query("""SELECT * FROM audit_log WHERE targetType = :type AND targetId = :targetId
        ORDER BY timestamp DESC""")
    fun observeTargetHistory(type: String, targetId: String): Flow<List<AuditLogEntity>>
    @Query("""SELECT * FROM audit_log WHERE visibleToAuthor = 1 AND userId = :userId
        ORDER BY timestamp DESC LIMIT :limit""")
    fun observeAuthorAudit(userId: String, limit: Int = 100): Flow<List<AuditLogEntity>>
    @Insert suspend fun log(entry: AuditLogEntity)
    @Query("DELETE FROM audit_log WHERE timestamp < :before")
    suspend fun purgeOld(before: Long)
    @Insert suspend fun logSecurity(entry: SecurityLogEntity)
    @Query("""SELECT * FROM security_log WHERE userId = :userId
        ORDER BY timestamp DESC LIMIT :limit""")
    fun observeUserSecurity(userId: String, limit: Int = 50): Flow<List<SecurityLogEntity>>
    @Query("DELETE FROM security_log WHERE timestamp < :before")
    suspend fun purgeOldSecurity(before: Long)
}
