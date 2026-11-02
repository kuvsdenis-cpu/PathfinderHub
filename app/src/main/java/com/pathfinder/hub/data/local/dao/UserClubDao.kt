package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.core.UserClubEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserClubDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: UserClubEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<UserClubEntity>)

    @Query("DELETE FROM user_clubs WHERE userId = :userId AND clubId = :clubId")
    suspend fun delete(userId: String, clubId: String)

    @Query("DELETE FROM user_clubs WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT clubId FROM user_clubs WHERE userId = :userId")
    suspend fun getClubIds(userId: String): List<String>

    @Query("SELECT clubId FROM user_clubs WHERE userId = :userId")
    fun observeClubIds(userId: String): Flow<List<String>>

    @Query("SELECT userId FROM user_clubs WHERE clubId = :clubId")
    suspend fun getUserIds(clubId: String): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM user_clubs WHERE userId = :userId AND clubId = :clubId)")
    suspend fun isMember(userId: String, clubId: String): Boolean
}
