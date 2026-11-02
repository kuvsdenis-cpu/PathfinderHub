package com.pathfinder.hub.data.repository

import com.pathfinder.hub.data.local.dao.InfoArticleDao
import com.pathfinder.hub.data.local.entity.info.InfoArticleEntity
import com.pathfinder.hub.data.remote.FirestoreInfoDataSource
import com.pathfinder.hub.data.sync.SyncQueueManager
import com.pathfinder.hub.domain.model.InfoArticle
import com.pathfinder.hub.domain.model.InfoCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeBaseRepository @Inject constructor(
    private val infoDao: InfoArticleDao,
    private val remote: FirestoreInfoDataSource,
    private val syncQueue: SyncQueueManager
) {
    fun getCategories(): Flow<List<InfoCategory>> = infoDao.observeAll().map { userArticles ->
        val base = baseKnowledgeBase.map { category ->
            val extra = userArticles.filter { it.categoryId == category.id }.map { it.toDomain() }
            category.copy(articles = category.articles + extra)
        }
        val newCategoryIds = userArticles.map { it.categoryId }.distinct().filter { id -> base.none { it.id == id } }
        val newCategories = newCategoryIds.map { catId ->
            InfoCategory(
                id = catId,
                title = catId,
                icon = "📁",
                articles = userArticles.filter { it.categoryId == catId }.map { it.toDomain() }
            )
        }
        base + newCategories
    }

    suspend fun saveArticle(categoryId: String, title: String, content: String, pdfUrl: String?, existingId: String? = null) {
        val now = Date()
        val id = existingId ?: UUID.randomUUID().toString()
        val existing = infoDao.getById(id)
        val entity = InfoArticleEntity(
            id = id,
            categoryId = categoryId,
            title = title,
            content = content,
            pdfUrl = pdfUrl,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            pendingSync = true
        )
        infoDao.upsert(entity)
        syncQueue.enqueue("info_article", id, "UPSERT", entity)
    }

    suspend fun deleteArticle(id: String) {
        infoDao.softDelete(id, Date())
        syncQueue.enqueue("info_article", id, "DELETE", mapOf("id" to id))
    }

    suspend fun pullFromCloud() {
        val remoteList = remote.pullAll()
        remoteList.forEach { remote ->
            val local = infoDao.getById(remote.id)
            if (local == null || local.updatedAt.time < remote.updatedAt.time) {
                infoDao.upsert(remote)
            }
        }
    }

    private fun InfoArticleEntity.toDomain() = InfoArticle(
        id = this.id,
        title = this.title,
        content = this.content,
        pdfAsset = this.pdfUrl
    )
}

val baseKnowledgeBase = listOf(
    InfoCategory(
        id = "uniform",
        title = "Положение о форме",
        icon = "👕",
        articles = listOf(
            InfoArticle(
                id = "uniform_general",
                title = "Положение о форме (PDF)",
                pdfAsset = "форма.pdf",
                content = "Полное положение о форме с картинками, схемами и таблицей сокращений."
            )
        )
    ),
    InfoCategory(
        id = "safety",
        title = "Безопасность",
        icon = "🛡️",
        articles = listOf(
            InfoArticle(
                id = "safety_planning",
                title = "Стратегическое планирование",
                pdfAsset = "safety2016.pdf",
                content = "Практическое руководство по безопасности."
            )
        )
    ),
    InfoCategory(
        id = "symbols",
        title = "Атрибутика и символы",
        icon = "⚜️",
        articles = listOf(
            InfoArticle(
                id = "symbols_et",
                title = "Атрибутика и символы",
                content = """
                   1. Красный цвет (жертва).
                   а. Напоминать нам о Христе. «Ибо так возлюбил Бог мир, что отдал Сына Своего Единородного, дабы всякий, верующий в Него, не погиб, но имел жизнь вечную» (Иоанн 3:16).
                   б. «Представьте тела ваши в жертву живую, святую, благоугодную Богу» (Рим. 12:1).
                   
                   2. Три стороны (треугольник).
                   а. Полнота Троицы — Отец, Сын, Дух Святой.
                   б. Три единства образования:
                      — Умственное — ремёсла и зачёты.
                      — Физическое — лагерные сборы.
                      — Духовное — миссионерская деятельность, свидетельствование.
                   
                   3. Золотистый цвет (отличие).
                   а. «Советую тебе купить у Меня золото, огнём очищенное, чтобы тебе обогатиться…» (Откр. 3:18).
                   б. Система оценки. Клуб следопытов предъявляет высокие требования, чтобы помочь создать сильный характер для Небесного царства.
                   
                   4. Щит (защита).
                   а. В Священном Писании часто упоминается щит Его народа. «Не бойся… Я твой щит» (Быт. 15:1; Еф. 6:16).
                   
                   5. Белый цвет (чистота).
                   а. «Побеждающий облечётся в белые одежды» (Откр. 3:5).
                   б. Мы желаем, чтобы жизнь наша была, как жизнь Христа, чистая и праведная.
                   
                   6. Клуб следопытов.
                   а. Клуб следопытов — одна из организаций всемирного молодёжного служения Церкви Адвентистов Седьмого Дня.
                   
                   7. Голубой цвет (верность).
                   а. Цель клуба следопытов — научить нас быть верными:
                      — нашему Богу на небесах;
                      — нашим родителям;
                      — нашей церкви.
                   
                   8. Меч (Библия).
                   а. Меч используется как оружие на войне. Сражение всегда выигрывают в наступлении. Мы ведём сражение против греха, наше оружие — слово Божье.
                   б. «…меч духовный, который есть слово Божье» (Еф. 6:17).
                """.trimIndent()
            )
        )
    ),
    InfoCategory(
        id = "anthem",
        title = "Гимн следопытов",
        icon = "🎵",
        articles = listOf(
            InfoArticle(
                id = "anthem_text",
                title = "Текст гимна",
                content = """
                   Зовут следопытами нас.
                   Мы Божии слуги все,
                   Верные во всякий час
                   В доброте и чистоте!
                   
                   Мы весть всему миру несём,
                   Свободу и Божью любовь.
                   Спаситель наш, Царь Иисус придёт
                   За нами вновь!
                   
                   Исследовать истину я
                   Хочу, словно следопыт,
                   И Творцу всего себя
                   Я желаю посвятить!
                   
                   Господь всем нам дарит любовь
                   И всякую благодать.
                   Я верю, что Царь Иисус Христос
                   Придёт опять!
                """.trimIndent()
            )
        )
    ),
    InfoCategory(
        id = "oath_law",
        title = "Клятва и Закон следопытов",
        icon = "🤝",
        articles = listOf(
            InfoArticle(
                id = "oath",
                title = "Клятва следопытов",
                content = """
                   С помощью Божьей
                   
                   Я клянусь быть честным, добрым и верным;
                   
                   Я буду соблюдать законы следопытов;
                   
                   Я буду слугой Бога и другом людей.
                """.trimIndent()
            ),
            InfoArticle(
                id = "law",
                title = "Закон следопытов",
                content = """
                   1. Начинать день с чтения Библии, утреннего стража и молитвы.
                   2. Честно исполнять свои обязательства.
                   3. Заботиться о своем теле.
                   4. Быть правдивым.
                   5. Быть вежливым и послушным.
                   6. С благоговением относиться к богослужению.
                   7. Жить с песней хвалы в сердце.
                   8. Исполнять Божьи повеления.
                """.trimIndent()
            )
        )
    ),
    InfoCategory(
        id = "organization",
        title = "Организация клуба",
        icon = "🏛️",
        articles = listOf(
            InfoArticle(
                id = "org_structure",
                title = "Организация следопытов",
                content = """
                   Клуб следопытов — это принятая во всём мире программа, организованная и направляемая отделами церковного служения Генеральной конференции Церкви Христиан — Адвентистов Седьмого Дня. Она предлагает широкий спектр познавательных мероприятий для подростков в возрасте от 8 до 15 лет и осуществляется местной адвентистской церковью под руководством и контролем молодёжного руководителя местной конференции.

                   Возглавляют любой клуб следопытов директора, наставники и учителя, копилан, секретарь и казначей. Клуб состоит из отдельных звеньев, от 6 до 8 следопытов в каждом звене, включая капитана и писаря.

                   Структура управления:
                   — Директор следопытских клубов при Генеральной конференции.
                   — Директор клуба следопытов при дивизионе.
                   — Директор клубов при Унионе.
                   — Директор клубов следопытов при местной конференции.
                   — Координаторы.
                   — Управление местной церкви.
                   — Директор клуба следопытов.
                   — Начальник штаба клуба следопытов.
                   — Заседание штаба.
                   — Заместитель директора по работе с мальчиками / девочками.
                   — Наставник.
                   — Капитан звена.
                   — Писарь звена.

                   Дополнительный персонал:
                   — Секретарь.
                   — Казначей.
                   — Инструктор по физической и строевой подготовке.
                   — Преподаватели.

                   Клуб следопытов имеет флаг с изображением эмблемы клуба. Флаг поднят во время проведения мероприятий и занятий клуба (местного и на уровне конференций). Имеется специально сконструированная полная парадная и полевая форма следопыта. Клуб следопытов имеет клятву и закон, отражающие духовную основу, на которых он строится.

                   Клуб функционирует в течение 10 календарных месяцев, начиная с февраля и заканчивая награждением в ноябре. Субботнее учение следопытов проводят на субботнем богослужении в течение часа с четвертью. Практические занятия проходят каждое второе воскресенье с утра в течение трёх часов.
                """.trimIndent()
            ),
            InfoArticle(
                id = "org_classes",
                title = "Классы и возрастные группы",
                content = """
                   Имеется 6 классов, каждый — под своим названием и с определённой возрастной группой. Каждый класс представлен своим цветом.

                   1. Друг — 10 лет — голубой
                   2. Спутник — 11 лет — красный
                   3. Исследователь — 12 лет — зелёный
                   4. Лесник — 13 лет — чёрный
                   5. Путешественник — 14 лет — бордовый
                   6. Проводник — 15 лет — золотистый

                   Следопыт, успешно завершивший курс подготовки в каком-либо классе, получает как награду значок (нашивку).
                """.trimIndent()
            )
        )
    )
)