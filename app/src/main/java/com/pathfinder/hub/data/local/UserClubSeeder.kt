package com.pathfinder.hub.data.local

import android.util.Log
import androidx.room.withTransaction
import com.pathfinder.hub.data.local.dao.UserClubDao
import com.pathfinder.hub.data.local.dao.UserDao
import com.pathfinder.hub.data.local.entity.core.UserClubEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B1: одноразовый досид user_clubs из users.clubIds (JSON).
 *
 * Миграция 26->27 сидит только из users.clubId.
 * Этот сидер добивает клубы, которые есть в clubIds, но не в clubId.
 *
 * Идемпотентен: INSERT OR IGNORE, можно звать при каждом старте.
 */
@Singleton
class UserClubSeeder @Inject constructor(
    private val db: AppDatabase,
    private val userDao: UserDao,
    private val userClubDao: UserClubDao,
) {

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val users = userDao.getAllWithClubIds()
            if (users.isEmpty()) return@withContext

            var added = 0
            db.withTransaction {
                for (user in users) {
                    val ids = buildSet {
                        user.clubId?.takeIf { it.isNotBlank() }?.let { add(it) }
                        addAll(user.clubIds.filter { it.isNotBlank() })
                    }
                    if (ids.isEmpty()) continue

                    val links = ids.map { UserClubEntity(userId = user.id, clubId = it) }
                    userClubDao.insertAll(links)
                    added += links.size
                }
            }
            Log.d(TAG, "user_clubs: обработано ${users.size} юзеров, вставлено до $added связей")
        } catch (e: Exception) {
            Log.e(TAG, "UserClubSeeder упал", e)
        }
    }

    companion object {
        private const val TAG = "UserClubSeeder"
    }
}
