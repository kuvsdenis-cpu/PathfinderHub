package com.pathfinder.hub.data.local.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.pathfinder.hub.data.local.entity.learning.ContentVersionEntity
import com.pathfinder.hub.data.local.entity.learning.HonorCategoryEntity
import com.pathfinder.hub.data.local.entity.learning.HonorEntity
import com.pathfinder.hub.data.local.entity.learning.HonorRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelEntity
import com.pathfinder.hub.data.local.entity.learning.LevelRequirementEntity
import com.pathfinder.hub.data.local.entity.learning.LevelSectionEntity
import com.pathfinder.hub.data.local.entity.learning.TestEntity
import com.pathfinder.hub.data.local.entity.learning.TestQuestionEntity
import com.pathfinder.hub.data.repository.HonorRepository
import com.pathfinder.hub.data.repository.LevelRepository
import com.pathfinder.hub.data.repository.StorageRepository
import com.pathfinder.hub.data.repository.TestRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Date

@HiltWorker
class SeedDatabaseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val levelRepository: LevelRepository,
    private val honorRepository: HonorRepository,
    private val storageRepository: StorageRepository,
    private val testRepository: TestRepository,
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "🚀 Проверяем версии контента…")
        return try {
            val levelsIndexJson = readAsset("levels/index.json")
            val categoriesJson = readAsset("honors/categories.json")
            val testsJson = readAsset("tests.json")

            if (levelsIndexJson == null && categoriesJson == null && testsJson == null) {
                Log.w(TAG, "⚠️ JSON не найдены в assets")
                return Result.failure()
            }

            val levelsVersion = extractVersion(levelsIndexJson)
            val honorsVersion = extractVersion(categoriesJson)
            val testsVersion = extractVersion(testsJson)

            val current = honorRepository.getLatestContentVersion()
            Log.d(TAG, "Текущие версии в Room: levels=${current?.levelsVersion}, " +
                    "honors=${current?.honorsVersion}, tests=${current?.testsVersion}")
            Log.d(TAG, "Версии в assets: levels=$levelsVersion, honors=$honorsVersion, tests=$testsVersion")

            var newLevelsVersion = current?.levelsVersion
            var newHonorsVersion = current?.honorsVersion
            var newTestsVersion = current?.testsVersion
            var updated = false
            val forceLevels = inputData.getBoolean("forceLevels", false)
            // После миграции 55->56 level_requirements может быть пустой —
            // тогда версия та же, но данные надо залить заново.
            val hasRequirements = try {
                levelRepository.observeAllRequirements().first().isNotEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось проверить level_requirements", e); true
            }
            if (!hasRequirements) Log.w(TAG, "level_requirements пуст — принудительный сид")

            // LEVELS
            if (levelsIndexJson != null && (forceLevels || !hasRequirements || shouldUpdate(current?.levelsVersion, levelsVersion))) {
                seedLevels(levelsIndexJson)
                newLevelsVersion = levelsVersion
                updated = true
            }

            // HONORS
            if (categoriesJson != null && shouldUpdate(current?.honorsVersion, honorsVersion)) {
                seedHonors()
                newHonorsVersion = honorsVersion
                updated = true
            }

            // TESTS
            if (testsJson != null && shouldUpdate(current?.testsVersion, testsVersion)) {
                seedTests(testsJson)
                newTestsVersion = testsVersion
                updated = true
            }

            if (updated) {
                val aggregateVersion = listOfNotNull(
                    newLevelsVersion,
                    newHonorsVersion,
                    newTestsVersion
                ).maxOrNull() ?: "0"

                honorRepository.upsertContentVersion(
                    ContentVersionEntity(
                        version = aggregateVersion,
                        updatedAt = Date(),
                        levels = 0,
                        honors = 0,
                        changelog = "Автообновление из assets (раздельные версии)",
                        levelsVersion = newLevelsVersion,
                        honorsVersion = newHonorsVersion,
                        testsVersion = newTestsVersion
                    )
                )
                Log.d(TAG, "✅ Контент обновлён. Агрегированная версия: $aggregateVersion")
            } else {
                Log.d(TAG, "✓ Версии не изменились")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка обновления БД", e)
            Result.failure()
        }
    }

    // ==================== ВЕРСИИ ====================

    private fun extractVersion(json: String?): String {
        if (json == null) return "0"
        return try {
            val obj = gson.fromJson(json, Map::class.java)
            (obj["version"] as? String) ?: "0"
        } catch (e: Exception) {
            "0"
        }
    }

    private fun shouldUpdate(current: String?, incoming: String): Boolean {
        if (incoming == "0" || incoming.isBlank()) return false
        if (current.isNullOrBlank()) return true
        return incoming != current
    }

    // ==================== УРОВНИ ====================

    private suspend fun seedLevels(indexJson: String) {
        // 1. Парсим index.json
        val index: LevelsIndex = gson.fromJson(indexJson, LevelsIndex::class.java)
            ?: throw IllegalStateException("levels/index.json пуст")
        if (index.files.isEmpty()) {
            throw IllegalStateException("levels/index.json: files пуст")
        }

        // 2. Читаем каждый файл как один уровень
        val parsedLevels = mutableListOf<ParsedLevel>()
        for (fileName in index.files) {
            val lvlJson = readAsset("levels/$fileName")
                ?: throw IllegalStateException("Asset не найден: levels/$fileName")
            try {
                val parsed: ParsedLevel = gson.fromJson(lvlJson, ParsedLevel::class.java)
                    ?: throw IllegalStateException("$fileName: пустой уровень")
                parsedLevels.add(parsed)
                Log.d(TAG, "  levels/$fileName: ${parsed.sections.size} секций")
            } catch (e: Exception) {
                throw IllegalStateException("Ошибка парсинга levels/$fileName: ${e.message}")
            }
        }

        val problems = mutableListOf<String>()
        for ((idx, parsed) in parsedLevels.withIndex()) {
            val lid = parsed.level.id.trim()
            if (lid.isBlank()) problems += "level[$idx]: пустой id"
            if (parsed.sections.isEmpty()) problems += "level[$idx] ($lid): 0 секций"
            var reqCount = 0
            for (sec in parsed.sections) {
                if (sec.id.isBlank()) problems += "level[$idx] ($lid): секция с пустым id"
                for (r in sec.requirements) {
                    if (r.id.isBlank()) problems += "level[$idx] ($lid)/${sec.id}: пустой id требования"
                    reqCount++
                }
            }
            if (reqCount == 0) problems += "level[$idx] ($lid): 0 требований"
            Log.d(TAG, "validate [$lid]: ${parsed.sections.size} секций, $reqCount требований")
        }
        if (problems.isNotEmpty()) {
            Log.e(TAG, "❌ levels.json невалиден:\n" + problems.joinToString("\n"))
            throw IllegalStateException("levels.json невалиден: ${problems.size} проблем")
        }

        levelRepository.seedLevelsAtomically {
            Log.d(TAG, "🧹 Очищаем levels/sections/requirements…")
            levelRepository.clearAllRequirements()
            levelRepository.clearAllSections()
            levelRepository.clearAllLevels()

            for (parsed in parsedLevels) {
                val lid = parsed.level.id.trim()
                levelRepository.upsertLevels(listOf(parsed.level.copy(id = lid)))

                val sections = parsed.sections.map { sec ->
                    LevelSectionEntity(
                        id = sec.id.trim(),
                        levelId = lid,
                        name = sec.name.trim(),
                        order = sec.order,
                    )
                }
                levelRepository.upsertSections(sections)

                val reqs = parsed.sections.flatMap { sec ->
                    sec.requirements.map { req ->
                        val imgs = req.imageUrls.map { path ->
                            storageRepository.buildLevelImageUrl(path)
                        }
                        req.copy(
                            id = req.id.trim(),
                            sectionId = sec.id.trim(),
                            levelId = lid,
                            text = req.text.trim(),
                            type = req.type.trim(),
                            imageUrls = imgs,
                            bookIds = req.bookIds ?: emptyList(),
                        )
                    }
                }
                levelRepository.upsertRequirements(reqs)
                Log.d(TAG, "✅ [$lid] ${sections.size} секций, ${reqs.size} требований")
            }
        }
        Log.d(TAG, "✅ levels пересижены атомарно")
    }

    // ==================== СПЕЦИАЛИЗАЦИИ ====================

    /**
     * Читает все JSON из assets/honors/ (кроме categories.json).
     */
    private fun readAllHonorsFiles(): List<ParsedHonor> {
        val all = mutableListOf<ParsedHonor>()
        val files = try {
            applicationContext.assets.list("honors") ?: emptyArray()
        } catch (e: Exception) {
            Log.w(TAG, "Папка assets/honors не найдена", e)
            emptyArray()
        }
        for (file in files.filter { it.endsWith(".json") && it != "categories.json" }) {
            val json = readAsset("honors/$file") ?: continue
            try {
                val type = object : TypeToken<List<ParsedHonor>>() {}.type
                val honors: List<ParsedHonor> = gson.fromJson(json, type)
                all.addAll(honors)
                Log.d(TAG, "  honors/$file: ${honors.size} специализаций")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка парсинга honors/$file", e)
            }
        }
        return all
    }

    private suspend fun seedHonors() {
        val categoriesJson = readAsset("honors/categories.json")
            ?: throw IllegalStateException("assets/honors/categories.json не найден")
        val categoriesFile: CategoriesFile =
            gson.fromJson(categoriesJson, CategoriesFile::class.java)
        honorRepository.upsertCategories(categoriesFile.categories)

        val allHonors = readAllHonorsFiles()
        if (allHonors.isEmpty()) {
            throw IllegalStateException("assets/honors/*.json — ни одного файла")
        }

        Log.d(TAG, "🧹 Очищаем старые специализации и требования…")
        honorRepository.clearAllHonors()
        honorRepository.clearAllHonorRequirements()

        val honors = allHonors.map { it.toEntity() }
        honorRepository.upsertHonors(honors)
        val requirements = allHonors.flatMap { it.requirements }
        honorRepository.upsertRequirements(requirements)

        Log.d(TAG, "Загружено: ${categoriesFile.categories.size} категорий, " +
                "${honors.size} специализаций, ${requirements.size} требований")
    }

    // ==================== ТЕСТЫ ====================

    private suspend fun seedTests(json: String) {
        val type = object : TypeToken<TestsFile>() {}.type
        val file: TestsFile = gson.fromJson(json, type)

        var testCount = 0
        var questionCount = 0
        for (parsed in file.tests) {
            val test = TestEntity(
                id = parsed.id,
                scope = parsed.scope,
                scopeId = parsed.scopeId,
                title = parsed.title,
                description = parsed.description,
                passScore = parsed.passScore,
                clubId = null,
                archivedAt = null,
            )
            testRepository.upsertTest(test)
            testCount++

            val questions = parsed.questions.mapIndexed { idx, q ->
                TestQuestionEntity(
                    id = q.id,
                    testId = parsed.id,
                    clubId = null,
                    honorId = "",
                    type = q.type,
                    text = q.text,
                    options = q.options,
                    correctAnswer = q.correctAnswer,
                    points = q.points,
                    order = idx,
                )
            }
            testRepository.replaceQuestions(parsed.id, questions)
            questionCount += questions.size
        }
        Log.d(TAG, "Загружено тестов: $testCount, вопросов: $questionCount")
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

// ==================== DTO ====================

data class LevelsIndex(
    val version: String,
    val files: List<String>
)

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

/** categories.json — только категории. */
data class CategoriesFile(
    val version: String,
    val categories: List<HonorCategoryEntity>
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
    @SerializedName("preview")
    val preview: String? = null,
    @SerializedName("img")
    val img: String? = null,
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
        contentVersion = contentVersion,
        previewUrl = preview,
        imageUrl = img
    )
}

data class TestsFile(
    val version: String,
    val tests: List<ParsedTest>
)

data class ParsedTest(
    val id: String,
    val scope: String,
    val scopeId: String?,
    val title: String,
    val description: String = "",
    val passScore: Int = 80,
    val questions: List<ParsedQuestion> = emptyList(),
)

data class ParsedQuestion(
    val id: String,
    val type: String,
    val text: String,
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val points: Int = 1,
)
