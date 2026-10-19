package com.pathfinder.hub.domain.usecase.auth

import android.util.Log
import com.pathfinder.hub.data.local.entity.core.UserEntity
import com.pathfinder.hub.data.repository.AuthRepository
import com.pathfinder.hub.data.repository.InviteRepository
import com.pathfinder.hub.data.repository.UserRepository
import com.pathfinder.hub.domain.usecase.club.TransferClubOwnershipUseCase
import java.util.Date
import javax.inject.Inject

/**
 * Регистрация по инвайт-коду с атомарным claim через Firestore.
 *
 * Порядок:
 *   1. reserveInvite   — Firestore: active → claiming (TTL 5 мин)
 *   2. registerWithEmail — Firebase Auth: создаём uid
 *   3. confirmInvite   — Firestore: claiming → used, usedBy = uid
 *   4. userRepo.upsert — Room + Firestore: создаём документ users/{uid}
 *   5. sendVerificationEmail — письмо с подтверждением почты
 *
 * Откат при падении:
 *   - Шаг 1 упал      → ничего не делаем, инвайт не тронут
 *   - Шаг 2 упал      → releaseInvite(code): claiming → active
 *   - Шаг 3 упал      → authRepo.deleteCurrentUser() + releaseInvite(code)
 *   - Шаг 4 упал      → authRepo.deleteCurrentUser() (инвайт уже used)
 *   - Шаг 5 упал      → не критично, пользователь запросит письмо повторно
 */
class RegisterByInviteUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val inviteRepo: InviteRepository,
    private val authRepo: AuthRepository,
    private val transferClubOwnership: TransferClubOwnershipUseCase,
) {

    companion object {
        private const val TAG = "RegisterByInviteUC"
    }

    suspend operator fun invoke(
        inviteCode: String,
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        birthDate: Date
    ): Result<String> {
        val code = inviteCode.trim().uppercase()
        val emailNorm = UserRepository.normalizeEmail(email)

        // ============================================================
        // ФАЗА 1: резервируем инвайт в Firestore
        // ============================================================
        val reserveResult = inviteRepo.reserveInvite(code)
        val invite = reserveResult.getOrElse { error ->
            Log.w(TAG, "reserveInvite упал: ${error.message}")
            return Result.failure(error)
        }

        Log.d(TAG, "reserveInvite OK: $code, role=${invite.role}, clubId=${invite.clubId}")

        // ============================================================
        // ФАЗА 2: создаём Firebase Auth
        // ============================================================
        val displayName = "$firstName $lastName"
        val authResult = authRepo.registerWithEmail(emailNorm, password, displayName)

        val uid = authResult.getOrElse { error ->
            Log.e(TAG, "registerWithEmail упал, откатываем reserve", error)
            inviteRepo.releaseInvite(code)
            return Result.failure(error)
        }

        Log.d(TAG, "registerWithEmail OK: uid=$uid")

        // ============================================================
        // ФАЗА 3: подтверждаем инвайт (claiming → used)
        // ============================================================
        val confirmResult = inviteRepo.confirmInvite(code, uid)
        confirmResult.getOrElse { error ->
            Log.e(TAG, "confirmInvite упал, откатываем Auth + reserve", error)
            authRepo.deleteCurrentUser()
            inviteRepo.releaseInvite(code)
            return Result.failure(error)
        }

        Log.d(TAG, "confirmInvite OK: $code → $uid")

        // ============================================================
        // ФАЗА 4: создаём User (Room + Firestore)
        // ============================================================
        val isConfSec = invite.role == "conference_secretary"
        val user = UserEntity(
            id = uid,
            email = emailNorm,
            emailVerified = false,
            firstName = firstName,
            lastName = lastName,
            role = invite.role,
            clubId = if (isConfSec) null else invite.clubId,
            clubIds = if (isConfSec) emptyList() else listOf(invite.clubId),
            // transferOwnership — owner будет назначен через transferClubOwnership.
            isOwnerClub = !invite.transferOwnership && invite.role !in listOf("director", "conference_secretary"),
            directorPermissions = if (invite.role == "director") invite.permissions else emptyList(),
            conferenceId = if (isConfSec) invite.conferenceId else null,
            birthDate = birthDate,
            avatar = null,
            parentEmail = null,
            parentalConsentStatus = if (invite.role == "teen") "pending" else "approved",
            parentalConsentRequestedAt = null,
            parentalConsentApprovedAt = null,
            accessLevel = if (invite.role == "teen") "limited" else "full",
            status = "active",
            deletionRequestedBy = null,
            deletionRequestedAt = null,
            anonymizedAt = null,
            permanentDeletionAt = null,
            onboardingCompleted = false,
            createdAt = Date(),
            lastLoginAt = null
        )

        return try {
            userRepo.upsert(user)
            Log.d(TAG, "userRepo.upsert OK: $uid")

            // ---- Передача владения клубом ----
            if (invite.transferOwnership) {
                val transfer = transferClubOwnership(
                    oldOwnerId = invite.createdBy,
                    newOwnerId = uid,
                    clubId = invite.clubId,
                    clubName = "",
                    userRole = "director",
                )
                if (transfer.isFailure) {
                    Log.e(TAG, "transferClubOwnership FAILED после upsert", transfer.exceptionOrNull())
                    // Не критично — user уже создан. Админ разберётся вручную.
                } else {
                    Log.d(TAG, "transferClubOwnership OK: ${invite.createdBy} -> $uid")
                }
            }

            // ============================================================
            // ФАЗА 5: отправляем письмо с подтверждением почты
            // ============================================================
            // Не блокируем регистрацию, если письмо не ушло —
            // пользователь сможет запросить его повторно из профиля.
            val mailResult = authRepo.sendVerificationEmail()
            mailResult.fold(
                onSuccess = { Log.d(TAG, "Письмо с подтверждением отправлено на $email") },
                onFailure = { e -> Log.w(TAG, "Не удалось отправить письмо: ${e.message}") }
            )

            Result.success(uid)
        } catch (e: Exception) {
            Log.e(TAG, "userRepo.upsert упал, откатываем Auth", e)
            // Инвайт уже used — release не сработает. Но Auth удалим,
            // чтобы не оставить «висячий» аккаунт.
            authRepo.deleteCurrentUser()
            Result.failure(e)
        }
    }
}