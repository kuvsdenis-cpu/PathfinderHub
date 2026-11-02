package com.pathfinder.hub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathfinder.hub.data.local.entity.sabbath.SabbathAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SabbathAnswerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(answer: SabbathAnswerEntity)

    @Query("SELECT * FROM sabbath_answers WHERE userId = :userId AND lessonId = :lessonId")
    fun observeAnswersForLesson(userId: String, lessonId: String): Flow<List<SabbathAnswerEntity>>

    @Query("SELECT * FROM sabbath_answers WHERE userId = :userId AND lessonId = :lessonId AND blockId = :blockId LIMIT 1")
    suspend fun getAnswer(userId: String, lessonId: String, blockId: String): SabbathAnswerEntity?
}
