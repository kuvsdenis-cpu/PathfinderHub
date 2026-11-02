package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.auth.ParentChildLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParentChildLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: ParentChildLinkEntity)

    @Query("SELECT * FROM parent_child_links WHERE parentId = :parentId AND revokedAt IS NULL")
    fun observeByParent(parentId: String): Flow<List<ParentChildLinkEntity>>

    @Query("SELECT * FROM parent_child_links WHERE childId = :childId AND revokedAt IS NULL")
    fun observeByChild(childId: String): Flow<List<ParentChildLinkEntity>>

    @Query("SELECT * FROM parent_child_links WHERE parentId = :parentId AND childId = :childId LIMIT 1")
    suspend fun getByParentAndChild(parentId: String, childId: String): ParentChildLinkEntity?

    @Query("SELECT * FROM parent_child_links WHERE clubId = :clubId")
    fun observeByClub(clubId: String): Flow<List<ParentChildLinkEntity>>
}
