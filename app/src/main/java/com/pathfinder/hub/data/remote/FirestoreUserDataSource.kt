package com.pathfinder.hub.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pathfinder.hub.data.local.entity.core.UserEntity
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    suspend fun getUser(uid: String): UserEntity? {
        return try {
            Log.d(TAG, "getUser(uid=$uid): запрос...")

            val snapshot = usersCollection.document(uid).get().await()

            Log.d(TAG, "getUser(uid=$uid): exists=${snapshot.exists()}, id=${snapshot.id}")

            if (!snapshot.exists()) {
                Log.w(TAG, "Документа users/$uid НЕ существует в Firestore")
                return null
            }

            val data = snapshot.data ?: run {
                Log.w(TAG, "snapshot.data == null для users/$uid")
                return null
            }

            Log.d(TAG, "raw data (${data.size} полей): $data")

            val user = mapToUserEntity(data)
            Log.d(TAG, "parsed OK: id=${user.id}, email=${user.email}, role=${user.role}, clubId=${user.clubId}")
            return user
        } catch (e: Exception) {
            Log.e(TAG, "getUser(uid=$uid) FAILED", e)
            return null
        }
    }

    suspend fun saveUser(user: UserEntity): Boolean = try {
        Log.d(TAG, "saveUser: id=${user.id}, role=${user.role}")
        usersCollection.document(user.id)
            .set(mapFromUserEntity(user), SetOptions.merge())
            .await()
        Log.d(TAG, "saveUser OK: ${user.id}")
        true
    } catch (e: Exception) {
        Log.e(TAG, "saveUser FAILED: ${user.id}", e)
        false
    }

    suspend fun updateUser(user: UserEntity): Boolean = try {
        Log.d(TAG, "updateUser: id=${user.id}")
        usersCollection.document(user.id)
            .set(mapFromUserEntity(user), SetOptions.merge())
            .await()
        Log.d(TAG, "updateUser OK: ${user.id}")
        true
    } catch (e: Exception) {
        Log.e(TAG, "updateUser FAILED: ${user.id}", e)
        false
    }

    // ==================== МАППИНГ ====================

    private fun mapToUserEntity(data: Map<String, Any?>): UserEntity {
        return UserEntity(
            id = data["id"] as? String ?: "",
            email = (data["email"] as? String ?: "").trim().lowercase(),
            emailVerified = data["emailVerified"] as? Boolean ?: false,
            firstName = data["firstName"] as? String ?: "",
            lastName = data["lastName"] as? String ?: "",
            role = data["role"] as? String ?: "teen",
            clubId = data["clubId"] as? String,
            clubIds = (data["clubIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isOwnerClub = data["isOwnerClub"] as? Boolean ?: true,
            directorPermissions = (data["directorPermissions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            conferenceId = data["conferenceId"] as? String,
            birthDate = parseDate(data["birthDate"]) ?: Date(),
            avatar = data["avatar"] as? String,
            parentEmail = data["parentEmail"] as? String,
            parentalConsentStatus = data["parentalConsentStatus"] as? String ?: "pending",
            parentalConsentRequestedAt = parseDate(data["parentalConsentRequestedAt"]),
            parentalConsentApprovedAt = parseDate(data["parentalConsentApprovedAt"]),
            accessLevel = data["accessLevel"] as? String ?: "limited",
            status = data["status"] as? String ?: "active",
            deletionRequestedBy = data["deletionRequestedBy"] as? String,
            deletionRequestedAt = parseDate(data["deletionRequestedAt"]),
            anonymizedAt = parseDate(data["anonymizedAt"]),
            permanentDeletionAt = parseDate(data["permanentDeletionAt"]),
            onboardingCompleted = data["onboardingCompleted"] as? Boolean ?: false,
            createdAt = parseDate(data["createdAt"]) ?: Date(),
            lastLoginAt = parseDate(data["lastLoginAt"]),
            syncedAt = parseDate(data["syncedAt"])
        )
    }

    private fun mapFromUserEntity(user: UserEntity): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["id"] = user.id
        map["email"] = user.email
        map["emailVerified"] = user.emailVerified
        map["firstName"] = user.firstName
        map["lastName"] = user.lastName
        map["role"] = user.role
        // Не пишем пустые clubId/clubIds — иначе затрём remote-данные
        if (!user.clubId.isNullOrBlank()) map["clubId"] = user.clubId
        if (user.clubIds.isNotEmpty()) map["clubIds"] = user.clubIds
        map["isOwnerClub"] = user.isOwnerClub
        map["directorPermissions"] = user.directorPermissions
        map["conferenceId"] = user.conferenceId
        map["birthDate"] = user.birthDate
        map["avatar"] = user.avatar
        map["parentEmail"] = user.parentEmail
        map["parentalConsentStatus"] = user.parentalConsentStatus
        map["parentalConsentRequestedAt"] = user.parentalConsentRequestedAt
        map["parentalConsentApprovedAt"] = user.parentalConsentApprovedAt
        map["accessLevel"] = user.accessLevel
        map["status"] = user.status
        map["deletionRequestedBy"] = user.deletionRequestedBy
        map["deletionRequestedAt"] = user.deletionRequestedAt
        map["anonymizedAt"] = user.anonymizedAt
        map["permanentDeletionAt"] = user.permanentDeletionAt
        map["onboardingCompleted"] = user.onboardingCompleted
        map["createdAt"] = user.createdAt
        map["lastLoginAt"] = user.lastLoginAt
        map["syncedAt"] = user.syncedAt
        return map
    }

    private fun parseDate(value: Any?): Date? {
        return when (value) {
            null -> null
            is Date -> value
            is com.google.firebase.Timestamp -> value.toDate()
            is Long -> Date(value)
            is String -> {
                val formats = listOf(
                    "MMM d, yyyy HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd"
                )
                for (fmt in formats) {
                    try {
                        return java.text.SimpleDateFormat(fmt, java.util.Locale.ENGLISH).parse(value)
                    } catch (_: Exception) {
                        // пробуем следующий
                    }
                }
                null
            }
            else -> null
        }
    }

    companion object {
        private const val TAG = "FirestoreUserDS"
    }
}