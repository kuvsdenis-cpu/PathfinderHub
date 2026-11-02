package com.pathfinder.hub.data.repository

import android.content.Context
import com.pathfinder.hub.data.local.dao.SabbathAnswerDao
import com.pathfinder.hub.data.local.entity.sabbath.SabbathAnswerEntity
import com.pathfinder.hub.data.remote.dto.sabbath.SabbathLessonDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SabbathSchoolRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val answerDao: SabbathAnswerDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SabbathLessonDto::class.java)

    suspend fun loadLesson(lessonId: String): SabbathLessonDto? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("sabbath_school/${lessonId}.json")
                .bufferedReader().use { it.readText() }
            adapter.fromJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveAnswer(userId: String, lessonId: String, blockId: String, text: String) {
        val id = "${userId}_${lessonId}_${blockId}"
        val entity = SabbathAnswerEntity(
            id = id,
            userId = userId,
            lessonId = lessonId,
            blockId = blockId,
            answerText = text
        )
        answerDao.upsert(entity)
    }

    fun observeAnswers(userId: String, lessonId: String): Flow<List<SabbathAnswerEntity>> {
        return answerDao.observeAnswersForLesson(userId, lessonId)
    }
}
