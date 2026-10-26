package com.pathfinder.hub.domain.usecase.director

import com.pathfinder.hub.data.local.SessionManager
import com.pathfinder.hub.data.local.entity.auth.InviteEntity
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import javax.inject.Inject

class CreateInviteUseCase @Inject constructor(
    private val inviteRepository: InviteRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) {
    /**
     * Создать инвайт-код от имени текущего директора.
     *
     * @param role — какую роль получит приглашённый:
     *               teen | parent | instructor | secretary | director
     * @param clubId — в какой клуб приглашаем. null = активный клуб.
     * @param permissions — права для director (co-director).
     */
    suspend operator fun invoke(
        role: String,
        clubId: String? = null,
        permissions: List<String> = emptyList(),
        childId: String? = null,
    ): Result<InviteEntity> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("Нет активной сессии"))

            val user = userRepository.getUser(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            // ============ Conference secretary ============
            // Создаётся только conference_admin'ом, привязка к conferenceId.
            if (role == "conference_secretary") {
                if (user.role != "conference_admin") {
                    return Result.failure(Exception(
                        "Только администратор конференции может приглашать секретаря конференции"
                    ))
                }
                val confId = user.conferenceId
                    ?: return Result.failure(Exception("У пользователя нет конференции"))
                val invite = inviteRepository.createInvite(
                    clubId = "",
                    conferenceId = confId,
                    role = "conference_secretary",
                    createdBy = userId,
                )
                return Result.success(invite)
            }

            // ============ Остальные роли — только director ============
            if (user.role != "director") {
                return Result.failure(Exception("Только директор может создавать инвайты"))
            }

            val targetClubId = clubId ?: user.clubId
                ?: return Result.failure(Exception("Не указан клуб"))

            val available = user.clubIds.ifEmpty { listOfNotNull(user.clubId) }
            if (targetClubId !in available) {
                return Result.failure(Exception("Нет доступа к этому клубу"))
            }

            val invite = inviteRepository.createInvite(
                clubId = targetClubId,
                role = role,
                createdBy = userId,
                permissions = if (role == "director") permissions else emptyList(),
                childId = if (role == "parent") childId else null,
            )
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}