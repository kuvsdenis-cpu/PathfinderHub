package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pathfinder.hub.data.local.entity.learning.ContentVersionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCategoryEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date

@HiltWorker
class SeedDatabaseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "🚀 Проверяем версию контента…")
        return try {
            val levelsJson = readAsset("levels.json")
            val honorsJson = readAsset("honors.json")

            if (levelsJson == null && honorsJson == null) {
                Log.w(TAG, "⚠️ JSON не найдены в assets")
                return Result.failure()
            }

            // Извлекаем версии из JSON
            val levelsVersion = extractVersion(levelsJson)
            val honorsVersion = extractVersion(honorsJson)

            // Текущая версия в Room
            val currentVersion = honorRepository.getLatestContentVersion()
            Log.d(TAG, "Текущая версия в Room: ${currentVersion?.version ?: "нет"}")
            Log.d(TAG, "Версия levels.json: $levelsVersion")
            Log.d(TAG, "Версия honors.json: $honorsVersion")

            // Нужно ли обновлять
            var updated = false

            if (levelsJson != null && shouldUpdate(currentVersion?.version, levelsVersion)) {
                seedLevels(levelsJson)
                updated = true
            }

            if (honorsJson != null && shouldUpdate(currentVersion?.version, honorsVersion)) {
                seedHonors(honorsJson)
                updated = true
            }

            // Сохраняем новую версию
            if (updated) {
                val newVersion = maxOf(levelsVersion, honorsVersion)
                honorRepository.upsertContentVersion(
                    ContentVersionEntity(
                        version = newVersion,
                        updatedAt = Date(),
                        levels = 0,
                        honors = 0,
                        changelog = "Автообновление из assets"
                    )
                )
                Log.d(TAG, "✅ Контент обновлён до версии $newVersion")
            } else {
                Log.d(TAG, "✓ Версия не изменилась, обновление не нужно")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка обновления БД", e)
            Result.failure()
        }
    }

    // ==================== ВЕРСИИ ====================

    /** Извлекает поле "version" из JSON без полного парсинга. */
    private fun extractVersion(json: String?): String {
        if (json == null) return "0"
        return try {
            val obj = gson.fromJson(json, Map::class.java)
            (obj["version"] as? String) ?: "0"
        } catch (e: Exception) {
            "0"
        }
    }

    /** Обновлять, если версия отличается от текущей. */
    private fun shouldUpdate(current: String?, incoming: String): Boolean {
        if (incoming == "0" || incoming.isBlank()) return false
        if (current.isNullOrBlank()) return true
        return incoming != current
    }

    // ==================== УРОВНИ ====================

    private suspend fun seedLevels(json: String) {
        val type = object : TypeToken<LevelsFile>() {}.type
        val file: LevelsFile = gson.fromJson(json, type)

        for (parsed in file.levels) {
            // Уровень
            levelRepository.upsertLevels(listOf(parsed.level))

            // Разделы
            val sections = parsed.sections.map { s ->
                LevelSectionEntity(
                    id = s.id,
                    levelId = s.levelId,
                    name = s.name,
                    order = s.order
                )
            }
            levelRepository.upsertSections(sections)

            // Требования
            val requirements = parsed.sections.flatMap { it.requirements }
            levelRepository.upsertRequirements(requirements)

            Log.d(TAG, "Уровень '${parsed.level.name}': " +
                    "${sections.size} разделов, ${requirements.size} требований")
        }
    }

    // ==================== СПЕЦИАЛИЗАЦИИ ====================

    private suspend fun seedHonors(json: String) {
        val type = object : TypeToken<HonorsFile>() {}.type
        val file: HonorsFile = gson.fromJson(json, type)

        // Категории
        honorRepository.upsertCategories(file.categories)

        // Специализации
        val honors = file.honors.map { it.toEntity() }
        honorRepository.upsertHonors(honors)

        // Требования
        val requirements = file.honors.flatMap { it.requirements }
        honorRepository.upsertRequirements(requirements)

        Log.d(TAG, "Загружено: ${file.categories.size} категорий, " +
                "${honors.size} специализаций, ${requirements.size} требований")
    }

    // ==================== УТИЛИТЫ ====================

    private fun readAsset(name: String): String? {
        return try {
            applicationContext.assets.open(name)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Asset не найден: $name", e)
            null
        }
    }

    companion object {
        const val TAG = "SeedDatabaseWorker"
        const val WORK_NAME = "seed_database"
    }
}

// ==================== DTO для парсинга JSON ====================

data class LevelsFile(
    val version: String,
    val levels: List<ParsedLevel>
)

data class ParsedLevel(
    val level: LevelEntity,
    val sections: List<ParsedSection>
)

data class ParsedSection(
    val id: String,
    val levelId: String,
    val name: String,
    val order: Int,
    val requirements: List<LevelRequirementEntity>
)

data class HonorsFile(
    val version: String,
    val categories: List<HonorCategoryEntity>,
    val honors: List<ParsedHonor>
)

data class ParsedHonor(
    val id: String,
    val name: String,
    val categoryId: String,
    val level: Int,
    val year: Int,
    val source: String,
    val description: String,
    val version: String,
    val isLocal: Boolean = false,
    val localClubId: String? = null,
    val scope: String = "global",
    val promotedAt: String? = null,
    val promotedBy: String? = null,
    val contentVersion: String = "2024.1",
    val requirements: List<HonorRequirementEntity> = emptyList()
) {
    fun toEntity(): HonorEntity = HonorEntity(
        id = id,
        name = name,
        categoryId = categoryId,
        level = level,
        year = year,
        source = source,
        description = description,
        version = version,
        isLocal = isLocal,
        localClubId = localClubId,
        scope = scope,
        promotedAt = null,
        promotedBy = promotedBy,
        contentVersion = contentVersion
    )
}