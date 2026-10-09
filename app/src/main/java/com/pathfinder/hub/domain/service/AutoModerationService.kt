package com.pathfinder.hub.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Автопроверка контента для детско-подростковой аудитории.
 *
 * Результаты:
 *   "passed"  — можно публиковать сразу
 *   "flagged" — требуется ручная модерация (персональные данные, спам, капс)
 *   "blocked" — жёсткая блокировка (мат, угрозы, экстремизм, наркотики, суицид)
 *
 * Логика:
 *   1. Нормализуем текст (латиница→кириллица, цифры→буквы, обходы).
 *   2. Ищем совпадения с чёрными списками.
 *   3. Если нашли "blocked"-паттерн — возвращаем BLOCKED.
 *   4. Иначе — ищем "flagged"-паттерны (контакты, спам).
 *   5. Проверяем структуру (капс, повторы, эмодзи).
 *
 * ВАЖНО: работает с детьми, поэтому ложноположительные срабатывания
 * предпочтительнее ложноотрицательных.
 */
@Singleton
class AutoModerationService @Inject constructor() {

    companion object {
        const val RESULT_PASSED = "passed"
        const val RESULT_FLAGGED = "flagged"
        const val RESULT_BLOCKED = "blocked"

        private const val MAX_EMOJI_RUN = 5
        private const val MAX_SAME_CHAR_RUN = 5
        private const val CAPS_THRESHOLD = 0.7f
        private const val MIN_LENGTH_FOR_CAPS = 10
    }

    // ============================================================
    // BLOCKED — жёсткая блокировка
    // ============================================================

    /** Русский мат (корни). Проверяются в нормализованном тексте. */
    private val bannedRootsRu = listOf(
        // Основной мат
        "бля", "блад", "блят", "блядь",
        "хуй", "хуя", "хую", "хуе", "хуё", "хер", "херн",
        "пизд", "пизж",
        "ебa", "ебал", "ебан", "ебат", "ебет", "ебёт", "еби", "ебуч",
        "ёбa", "ёбал", "ёбан", "ёбат", "ёбет", "ёбёт", "ёби",
        "сукa", "сукu", "сучк", "сучон",
        "мудaк", "мудак", "мудил", "мудозвон",
        "пидор", "пидар", "пидр", "педик", "педик",
        "залуп", "залупa", "залупу",
        "дроч", "дрочи",
        "жопa", "жопы", "жопе", "жопу",
        "срак", "срaк", "срать", "сру",
        "гaндон", "гандон", "гондон", "гандо",
        "шлюх", "шлюш", "шалав",
        "простит", "проститут",
        "мaндa", "манда", "мандав",
        "мокрa", "мокрощ", "мохнaт",
        "хрен", "хренов",
        "педр", "петух",
        "уёб", "уебa", "уеб",
        "выёб", "выеб",
        "отъеб", "отеб",
        "наёб", "наеб",
        "долбоёб", "долбоеб",
        "мозгоёб", "мозгоеб",
        "хуеплёт", "хуеплет",
        "жополиз",
        "мандохвaт",
        "говн", "говён",
        "срач",
        "ссат", "ссaть", "ссу",
        "нехер", "нех",
        "ахуе", "ахуй", "ахуен",
        "охуе", "охуй", "охуен",
        "похуи", "похуй", "похуист",
        "нахуя", "нахуй",
        "дохуя",
        "нихуя", "нихуи",
        "какой-то хуй", "какого хуя",
    )

    /** Транслит мата. */
    private val bannedRootsTranslit = listOf(
        "blya", "blad", "blyad", "blyat",
        "hui", "huy", "xui", "xuy", "huya", "huja",
        "pizd", "pizda", "pizdec", "pizdec",
        "eba", "ebal", "ebat", "eban",
        "yoba", "yob",
        "suka", "suchk", "suck",
        "mudak", "mudil", "mudo",
        "pidar", "pidor", "pidr",
        "zalup",
        "droch", "drochi",
        "zhopa", "jopa",
        "srak", "srat",
        "gandon", "gondon",
        "shlux", "shlyuha",
        "prostitut",
        "govn", "govno",
        "mand", "manda",
        "hren", "hrenov",
        "ueb", "ueba",
        "dolboeb",
        "ohue", "ohui", "ohuenno",
        "pohui", "pohuy",
        "nahuy", "nahui",
        "nihuya", "nihuja",
    )

    /** Английский мат. */
    private val bannedRootsEn = listOf(
        "fuck", "fck", "fuk", "phuck",
        "shit", "sh1t",
        "bitch", "b1tch",
        "cunt",
        "dick", "d1ck",
        "ass hole", "asshole", "a-hole",
        "pussy",
        "bastard",
        "whore",
        "slut",
        "nigger", "n1gger", "niga",
        "faggot", "fag",
        "motherfucker",
        "cocksucker",
        "dumbass",
        "jackass",
    )

    /** Угрозы и насилие. */
    private val threats = listOf(
        "убью", "убей", "зарежу", "застрелю", "прикончу",
        "прибью", "придушу", "задушу", "удушу",
        "сломаю", "разобью", "порву", "отрежу",
        "изобью", "поколочу", "изнасилую",
        "изуродую", "выколю",
        "сожгу", "подожгу",
        "закопаю",
        "найду тебя", "знаю где ты живёшь", "знаю где живешь",
        "дома тебе покажу", "после школы",
        "килл", "убить", "насилие",
        "я тебя", "ты труп", "ты покойник",
        "лучше молись", "молись",
        "kill you", "i will kill", "murder",
        "destroy you", "hunt you",
    )

    /** Экстремизм, ненависть, дискриминация. */
    private val hateSpeech = listOf(
        "убей всех", "всех убить", "смерть всем",
        "нацист", "нацизм", "фашист", "фашизм", "гитлер",
        "heil", "хайль",
        "zиг", "зиг хайль",
        "чурк", "хач", "жид", "жиды", "черножоп",
        "хохол", "москаль", "ватник", "укроп",
        "кавказц", "армяшк", "узбек",
        "ненавижу всех", "убить всех",
        "терроризм", "теракт", "взорву", "бомба",
        "взорвать", "устрою взрыв",
        "я сделаю теракт",
        "ненавижу россию", "ненавижу украину",
        "слава украине", "за россию",
        "путин", "zelensky",
        "сво", "сво-шник", "свошник",
    )

    /** Наркотики и запрещённые вещества. */
    private val drugs = listOf(
        "наркот", "наркоман", "наркозависим",
        "героин", "кокаин", "кокс", "метамфетамин", "мефедрон",
        "меф", "мет", "амфетамин", "амф",
        "марихуан", "марихун", "гашиш", "гаш", "план", "травка",
        "лсд", "лсд-25", "кислота",
        "экстази", "мдма",
        "спайс", "спайс-",
        "соли для ванн", "скорость для ванн",
        "медленный", "медленная",
        "закладк", "клад", "закладка",
        "приобрести наркот", "купить наркот",
        "где взять наркот", "где достать",
        "продам наркот", "продаю наркот",
        "запрещённые вещества",
        "marijuana", "cocaine", "heroin", "meth", "weed",
        "где найти траву", "где купить траву",
    )

    /** Суицид и самоповреждение. */
    private val selfHarm = listOf(
        "покончить с собой", "покончу с собой",
        "убить себя", "убью себя",
        "свести счёты с жизнью",
        "хочу умереть", "хочу сдохнуть",
        "нет смысла жить", "жизнь не имеет смысла",
        "покончить жизнь", "покончу жизнь",
        "спрыгнуть с крыши", "спрыгну с крыши",
        "перерезать вены", "вскрыть вены",
        "выпить таблетки", "передоз",
        "суицид", "самоубийство",
        "kill myself", "suicide", "end my life",
    )

    /** Педифилия/сексуальный контент с детьми. */
    private val childSafety = listOf(
        "интим с детьми", "секс с детьми",
        "разврат с детьми",
        "детское порно", "детская порнография",
        "cp", "child porn",
        "педофил", "педо",
        "встреча с детьми", "пойдём ко мне домой",
        "пойдем ко мне домой", "пошли ко мне домой",
        "приходи ко мне домой",
    )

    /** Сексуальный контент (взрослый). */
    private val sexualContent = listOf(
        "секс", "секса", "заниматься сексом",
        "минет", "орал", "анал", "вагин", "пенис",
        "член", "хуем", "оргазм", "эякул",
        "порно", "порнограф", "порнух",
        "голая", "голый", "раздевание",
        "трахнуть", "трахну", "переспать",
        "заняться любовью",
    )

    // ============================================================
    // FLAGGED — отправляем на ручную модерацию
    // ============================================================

    private val emailRegex = Regex(
        """\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""
    )

    private val phoneRegex = Regex(
        """(?:\+?\d{1,3}[\s\-]?)?(?:\(?\d{3,4}\)?[\s\-]?)?\d{3}[\s\-]?\d{2}[\s\-]?\d{2}"""
    )

    private val urlRegex = Regex(
        """(?:https?://|www\.)\S+|\b[a-z0-9\-]+\.(?:ru|com|org|net|io|su|рф|онлайн)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Прямые упоминания мессенджеров и соцсетей. */
    private val socialTriggers = listOf(
        "whatsapp", "ватсап", "ватцап", "вотсап",
        "telegram", "телеграм", "телега", "тг", "tg:",
        "vk.com", "вконтакте", "вк:",
        "instagram", "инстаграм", "инста", "inst:",
        "tiktok", "тикток", "тик-ток",
        "snapchat", "снапчат",
        "discord", "дискорд",
        "signal", "сигнал",
        "viber", "вайбер",
        "youtube", "ютуб",
        "twitch", "твич",
    )

    /** Адреса и школы. */
    private val addressTriggers = listOf(
        "ул.", "улица", "проспект", "пр-т", "переулок",
        "школа №", "школа #", "гимназия", "лицей",
        "класс:", "класс №", "5а", "6а", "7а", "8а", "9а", "10а", "11а",
        "5б", "6б", "7б", "8б", "9б", "10б", "11б",
        "дом ", "квартира", "кв. ", "подъезд",
        "живу на", "живу в", "мой адрес",
    )

    /** Спам-триггеры. */
    private val spamTriggers = listOf(
        "купи", "купите", "закажи", "закажите",
        "скидк", "акци", "распродаж", "промокод",
        "заработ", "заработай", "заработать",
        "быстрые деньги", "лёгкие деньги", "легкие деньги",
        "инвестиц", "вложи", "вложите",
        "криптовалют", "криптa", "биткоин", "битк",
        "подписывайся", "подпишись", "переходи по ссылке",
        "перейди по ссылке", "жми ссылку",
        "выигра", "розыгрыш", "приз", "призыв",
        "бесплатно только сегодня",
        "не упусти шанс",
        "только сегодня",
        "скинь номер", "скиньте номер",
        "дай номер", "дайте номер",
        "обменяемся номерами",
    )

    // ============================================================
    // ПУБЛИЧНЫЙ API
    // ============================================================

    fun check(text: String): String {
        if (text.isBlank()) return RESULT_PASSED

        val normalized = normalize(text)

        // 1. Жёсткая блокировка
        if (containsAny(normalized, bannedRootsRu)) return RESULT_BLOCKED
        if (containsAny(normalized, bannedRootsTranslit)) return RESULT_BLOCKED
        if (containsAny(text.lowercase(), bannedRootsEn)) return RESULT_BLOCKED
        if (containsAny(normalized, threats)) return RESULT_BLOCKED
        if (containsAny(normalized, hateSpeech)) return RESULT_BLOCKED
        if (containsAny(normalized, drugs)) return RESULT_BLOCKED
        if (containsAny(normalized, selfHarm)) return RESULT_BLOCKED
        if (containsAny(normalized, childSafety)) return RESULT_BLOCKED
        if (containsAny(normalized, sexualContent)) return RESULT_BLOCKED

        // 2. Мягкая пометка (флаг → ручная модерация)
        if (emailRegex.containsMatchIn(text)) return RESULT_FLAGGED
        if (phoneRegex.containsMatchIn(text)) return RESULT_FLAGGED
        if (urlRegex.containsMatchIn(text)) return RESULT_FLAGGED
        if (containsAny(normalized, socialTriggers)) return RESULT_FLAGGED
        if (containsAny(normalized, addressTriggers)) return RESULT_FLAGGED
        if (containsAny(normalized, spamTriggers)) return RESULT_FLAGGED

        // 3. Структурные проверки
        if (hasRepeatedCharRun(text, MAX_SAME_CHAR_RUN)) return RESULT_FLAGGED
        if (hasEmojiOverflow(text)) return RESULT_FLAGGED
        if (isShouting(text)) return RESULT_FLAGGED

        return RESULT_PASSED
    }

    // ============================================================
    // ВНУТРЕННИЕ ФУНКЦИИ
    // ============================================================

    /**
     * Нормализация для обхода тривиальных обфускаций.
     *   - латиница, визуально похожая на кириллицу → кириллица
     *   - цифры → буквы
     *   - спецсимволы → ничего
     *   - повторяющиеся буквы → сжимаются
     */
    private fun normalize(input: String): String {
        var s = input.lowercase()

        // Цифры и символы-обходы
        s = s
            .replace('0', 'о')
            .replace('1', 'и')
            .replace('3', 'з')
            .replace('4', 'ч')
            .replace('6', 'б')
            .replace('@', 'а')
            .replace('$', 's')
            .replace('!', 'i')

        // Латиница → похожая кириллица (для ловли транслита и микса)
        s = s
            .replace('a', 'а')
            .replace('e', 'е')
            .replace('o', 'о')
            .replace('c', 'с')
            .replace('p', 'р')
            .replace('x', 'х')
            .replace('y', 'у')
            .replace('k', 'к')
            .replace('m', 'м')
            .replace('t', 'т')
            .replace('h', 'н')
            .replace('b', 'в')

        // Убираем всё, кроме букв
        s = s.replace(Regex("[^а-яёa-z]"), "")

        // Сжимаем повторы: "сууука" → "сука", "ппппизд" → "пизд"
        s = s.replace(Regex("(.)\\1+"), "$1")

        return s
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean {
        for (needle in needles) {
            if (haystack.contains(needle.lowercase())) return true
        }
        return false
    }

    private fun hasRepeatedCharRun(text: String, max: Int): Boolean {
        if (text.isEmpty()) return false
        var run = 1
        var prev = text[0]
        for (i in 1 until text.length) {
            val c = text[i]
            if (c == prev && c.isLetter()) {
                run++
                if (run > max) return true
            } else {
                run = 1
                prev = c
            }
        }
        return false
    }

    private fun hasEmojiOverflow(text: String): Boolean {
        var run = 0
        for (c in text) {
            if (isEmoji(c)) {
                run++
                if (run > MAX_EMOJI_RUN) return true
            } else {
                run = 0
            }
        }
        return false
    }

    private fun isEmoji(c: Char): Boolean {
        val code = c.code
        return code in 0x1F300..0x1FAFF ||
               code in 0x2600..0x27BF ||
               code in 0x1F000..0x1F2FF
    }

    private fun isShouting(text: String): Boolean {
        if (text.length < MIN_LENGTH_FOR_CAPS) return false
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val upper = text.count { it.isUpperCase() }
        return upper.toFloat() / letters > CAPS_THRESHOLD
    }
}
