package com.pathfinder.hub.data.repository
import com.pathfinder.hub.data.local.dao.AuditDao
import com.pathfinder.hub.data.local.entity.audit.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuditRepository @Inject constructor(private val dao: AuditDao) {
    fun observeClubAudit(c: String, l: Int = 200): Flow<List<AuditLogEntity>> = dao.observeClubAudit(c, l)
    fun observeTargetHistory(t: String, id: String): Flow<List<AuditLogEntity>> = dao.observeTargetHistory(t, id)
    fun observeAuthorAudit(u: String, l: Int = 100): Flow<List<AuditLogEntity>> = dao.observeAuthorAudit(u, l)
    suspend fun log(e: AuditLogEntity) = dao.log(e)
    suspend fun purgeOld(b: Long) = dao.purgeOld(b)
    suspend fun logSecurity(e: SecurityLogEntity) = dao.logSecurity(e)
    fun observeUserSecurity(u: String, l: Int = 50): Flow<List<SecurityLogEntity>> = dao.observeUserSecurity(u, l)
    suspend fun purgeOldSecurity(b: Long) = dao.purgeOldSecurity(b)
}
