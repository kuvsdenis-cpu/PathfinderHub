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
     *               teen | parent | instructor | secretary
     */
    suspend operator fun invoke(role: String): Result<InviteEntity> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("Нет активной сессии"))

            val user = userRepository.getUser(userId)
                ?: return Result.failure(Exception("Пользователь не найден"))

            if (user.role != "director") {
                return Result.failure(Exception("Только директор может создавать инвайты"))
            }

            val clubId = user.clubId
                ?: return Result.failure(Exception("У пользователя нет клуба"))

            val invite = inviteRepository.createInvite(
                clubId = clubId,
                role = role,
                createdBy = userId
            )
            Result.success(invite)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}