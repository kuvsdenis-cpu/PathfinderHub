package com.pathfinder.hub.domain.usecase.audit
import com.pathfinder.hub.data.repository.AuditRepository
import com.pathfinder.hub.data.local.entity.audit.AuditLogEntity
import javax.inject.Inject

class ExportAuditUseCase @Inject constructor(private val repo: AuditRepository) {
    suspend operator fun invoke(clubId: String): Result<List<AuditLogEntity>> = try {
        // TODO: экспорт в PDF/CSV
        Result.success(emptyList())
    } catch (e: Exception) { Result.failure(e) }
}
