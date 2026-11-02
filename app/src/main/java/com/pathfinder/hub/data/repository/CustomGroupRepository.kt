package com.pathfinder.hub.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.Gson
import com.pathfinder.hub.data.local.AppDatabase
import com.pathfinder.hub.data.local.dao.CustomGroupDao
import com.pathfinder.hub.data.local.entity.groups.CustomGroupEntity
import com.pathfinder.hub.data.local.entity.groups.CustomGroupRule
import com.pathfinder.hub.data.sync.SyncQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B1: пользовательские группы.
 *
 * Ответственность:
 *   - CRUD над CustomGroupEntity;
 *   - сериализация CustomGroupRule ↔ ruleJson (Gson);
 *   - resolveMembers(groupId) → список userId по правилу;
 *   - resolveMembersFromRule(rule) → то же, но без сохранённой группы.
 */
@Singleton
class CustomGroupRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: CustomGroupDao,
    private val exclusionDao: com.pathfinder.hub.data.local.dao.CustomGroupExclusionDao,
    private val userDao: com.pathfinder.hub.data.local.dao.UserDao,
    private val syncQueueManager: SyncQueueManager,
) {

    private val gson = Gson()

    // ---------- Чтение ----------

    fun observeActiveByClub(clubId: String): Flow<List<CustomGroupEntity>> =
        dao.observeActiveByClub(clubId)

    fun observeArchivedByClub(clubId: String): Flow<List<CustomGroupEntity>> =
        dao.observeArchivedByClub(clubId)

    suspend fun getById(id: String): CustomGroupEntity? = dao.getById(id)

    fun decodeRule(entity: CustomGroupEntity): CustomGroupRule =
        try {
            gson.fromJson(entity.ruleJson, CustomGroupRule::class.java) ?: CustomGroupRule()
        } catch (_: Exception) {
            CustomGroupRule()
        }

    fun encodeRule(rule: CustomGroupRule): String = gson.toJson(rule)

    // ---------- Запись ----------

    suspend fun upsert(group: CustomGroupEntity, rule: CustomGroupRule) {
        val now = System.currentTimeMillis()

        // Принудительные участники: owner клуба + создатель.
        val owner = try { userDao.getClubOwner(group.clubId)?.id } catch (_: Exception) { null }
        val forced = listOfNotNull(owner, group.createdBy).distinct()
        val forcedJson = gson.toJson(forced)

        val entity = group.copy(
            ruleJson = encodeRule(rule),
            forcedMemberIdsJson = forcedJson,
            updatedAt = Date(now),
        )
        db.withTransaction {
            dao.upsert(entity)
            // Создатель всегда «в группе» — снимаем возможное исключение.
            exclusionDao.delete(entity.id, entity.createdBy)
            syncQueueManager.enqueue("custom_group", entity.id, "CREATE", entity)
        }
    }

    fun decodeForcedMemberIds(entity: CustomGroupEntity): List<String> =
        try {
            val arr = gson.fromJson(entity.forcedMemberIdsJson, Array<String>::class.java)
            arr?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun delete(id: String) {
        db.withTransaction {
            val existing = dao.getById(id)
            dao.deleteById(id)
            if (existing != null) {
                syncQueueManager.enqueue("custom_group", id, "DELETE", existing)
            }
        }
    }

    suspend fun archive(id: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val purgeAfter = now + 90L * 24 * 3600 * 1000
            dao.archive(id, now, purgeAfter)
            dao.getById(id)?.let { syncQueueManager.enqueue("custom_group", id, "UPDATE", it) }
        }
    }

    suspend fun restore(id: String) {
        db.withTransaction {
            dao.restore(id)
            dao.getById(id)?.let { syncQueueManager.enqueue("custom_group", id, "UPDATE", it) }
        }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int =
        db.withTransaction { dao.purgeExpired(now) }

    // ---------- Резолв участников ----------

    /**
     * Резолв участников по сохранённой группе.
     * Если группа не найдена — пустой список.
     */
    suspend fun resolveMembers(groupId: String): List<String> {
        val entity = dao.getById(groupId) ?: return emptyList()
        if (entity.archivedAt != null) return emptyList()
        val byRule = resolveMembersFromRule(entity.clubId, decodeRule(entity))
        val forced = decodeForcedMemberIds(entity)
        val excluded = exclusionDao.excludedUserIds(groupId).toSet()
        return (byRule + forced).distinct().filterNot { it in excluded }
    }

    /**
     * Резолв участников по правилу.
     * Логика: И между полями, ИЛИ внутри поля.
     *
     * Мультиклубность: проверяем через user_clubs (EXISTS),
     * а не через u.clubId — так teen, у которого клуб вторичный,
     * тоже попадёт в выборку.
     *
     * Удалённых не берём: deletionRequestedAt / anonymizedAt /
     * permanentDeletionAt — все должны быть NULL.
     *
     * LEFT JOIN заменён на EXISTS: пользователи без завершённых
     * ступеней/специализаций больше не выпадают из выборки.
     */
    suspend fun resolveMembersFromRule(clubId: String, rule: CustomGroupRule): List<String> {
        val sb = StringBuilder()
        val args = mutableListOf<Any>()

        sb.append("SELECT DISTINCT u.id FROM users u")
        sb.append(" WHERE EXISTS (SELECT 1 FROM user_clubs uc")
        sb.append("   WHERE uc.userId = u.id AND uc.clubId = ?)")
        args.add(clubId)

        val roles = rule.roles.distinct()
        if (roles.isNotEmpty()) {
            sb.append(" AND u.role IN (").append(placeholders(roles.size)).append(")")
            args.addAll(roles)
        }

        val statuses = rule.statuses.distinct()
        if (statuses.isNotEmpty()) {
            sb.append(" AND u.status IN (").append(placeholders(statuses.size)).append(")")
            args.addAll(statuses)
        }

        if (rule.isOwnerClub != null) {
            sb.append(" AND u.isOwnerClub = ?")
            args.add(if (rule.isOwnerClub) 1 else 0)
        }

        sb.append(" AND u.deletionRequestedAt IS NULL")
        sb.append(" AND u.anonymizedAt IS NULL")
        sb.append(" AND u.permanentDeletionAt IS NULL")

        val levels = rule.levelIds.distinct()
        if (levels.isNotEmpty()) {
            sb.append(" AND EXISTS (SELECT 1 FROM level_completions lc")
            sb.append("   WHERE lc.userId = u.id AND lc.levelId IN (")
            sb.append(placeholders(levels.size)).append("))")
            args.addAll(levels)
        }

        val honors = rule.honorIds.distinct()
        if (honors.isNotEmpty()) {
            sb.append(" AND EXISTS (SELECT 1 FROM honor_completions hc")
            sb.append("   WHERE hc.userId = u.id AND hc.honorId IN (")
            sb.append(placeholders(honors.size)).append("))")
            args.addAll(honors)
        }

        val query = SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
        return dao.resolveMembers(query)
    }

    // ---------- Выход / повторный вход ----------

    /**
     * Выход из группы. Создателя блокируем — он должен передать группу.
     * Возвращает Result.failure, если выйти нельзя.
     */
    suspend fun leaveGroup(groupId: String, userId: String): Result<Unit> {
        val group = dao.getById(groupId)
            ?: return Result.failure(IllegalStateException("Группа не найдена"))
        if (group.createdBy == userId) {
            return Result.failure(IllegalStateException(
                "Создатель не может покинуть группу. Передайте группу другому сотруднику."
            ))
        }
        val now = Date()
        val exclusion = com.pathfinder.hub.data.local.entity.groups.CustomGroupExclusionEntity(
            groupId = groupId,
            userId = userId,
            excludedAt = now,
            excludedBy = userId,
            reason = "left",
            pendingSync = true,
        )
        db.withTransaction {
            exclusionDao.upsert(exclusion)
            syncQueueManager.enqueue(
                entityType = "custom_group_exclusion",
                entityId = "$groupId:$userId",
                operation = "UPSERT",
                payload = exclusion,
            )
        }
        return Result.success(Unit)
    }

    suspend fun rejoinGroup(groupId: String, userId: String) {
        db.withTransaction {
            val existing = exclusionDao.getExclusion(groupId, userId)
            exclusionDao.delete(groupId, userId)
            if (existing != null) {
                syncQueueManager.enqueue(
                    entityType = "custom_group_exclusion",
                    entityId = "$groupId:$userId",
                    operation = "DELETE",
                    payload = existing,
                )
            }
        }
    }

    suspend fun isExcluded(groupId: String, userId: String): Boolean =
        exclusionDao.getExclusion(groupId, userId) != null

    private fun placeholders(n: Int): String = List(n) { "?" }.joinToString(",")
}
