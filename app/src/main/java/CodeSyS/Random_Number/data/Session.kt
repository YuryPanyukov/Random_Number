package CodeSyS.Random_Number.data

import CodeSyS.Random_Number.domain.CoinSide
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Запись журнала генерации: одно выданное число и время выдачи.
 *
 * Нужна для экспорта истории с таймстампами, статистики «за период»
 * и отладки розыгрышей. Старые данные (просто [Session.generated])
 * читаются как записи с временем `0` — см. [Session] и [SessionCodec].
 */
@Serializable
data class GeneratedEntry(
    val value: Int,
    val at: Long,
) {
    /** Запись без времени (для данных, сохранённых до появления журнала). */
    fun toInt(): Int = value
}

/**
 * Сохранённая сессия генерации случайных чисел.
 *
 * Параметры режима генерации вынесены в [payload] ([SessionPayload]) —
 * это точка расширения для новых режимов (кубики, монетка, списки):
 * журнал, время и seed общие, меняется только «что и как генерируется».
 *
 * Для режима чисел есть плоский конструктор и свойства-делегаты
 * (`min`/`max`/`allowRepeats`), поэтому существующий код и данные
 * не изменились.
 *
 * @param id уникальный идентификатор сессии.
 * @param title человекочитаемое имя (например, «1..100, без повторов»).
 * @param payload параметры режима генерации.
 * @param log журнал выдачи с таймстампами.
 * @param createdAt время создания сессии (epoch millis).
 * @param lastUsedAt время последнего использования (epoch millis),
 * сессии сортируются по убыванию этого поля.
 * @param seed значение seed для воспроизводимой генерации
 * (`null` — генерация невоспроизводима).
 * @param seedFromSecure `true`, если seed выбран криптографическим
 * источником ([java.security.SecureRandom]) — сессии честного розыгрыша.
 * Показывается в шаринге результата как «источник».
 * @param tags пользовательские теги (без пустых и повторов).
 * @param isFavorite `true` — сессия в избранном.
 * @param isArchived `true` — сессия в архиве (скрыта из общего списка).
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    val payload: SessionPayload,
    val log: List<GeneratedEntry> = emptyList(),
    val createdAt: Long = 0L,
    val lastUsedAt: Long = createdAt,
    val seed: Long? = null,
    val seedFromSecure: Boolean = false,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
) {
    /**
     * Плоский конструктор для режима чисел.
     *
     * Оставлен, чтобы существующий код (в том числе тесты) не зависел
     * от структуры payload; новые режимы конструируются payload-ом напрямую.
     */
    constructor(
        id: String,
        title: String,
        min: Int,
        max: Int,
        allowRepeats: Boolean,
        log: List<GeneratedEntry> = emptyList(),
        createdAt: Long = 0L,
        lastUsedAt: Long = createdAt,
        seed: Long? = null,
        seedFromSecure: Boolean = false,
        tags: List<String> = emptyList(),
        isFavorite: Boolean = false,
        isArchived: Boolean = false,
    ) : this(
        id = id,
        title = title,
        payload = SessionPayload.Numbers(min = min, max = max, allowRepeats = allowRepeats),
        log = log,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        seed = seed,
        seedFromSecure = seedFromSecure,
        tags = tags,
        isFavorite = isFavorite,
        isArchived = isArchived,
    )

    /**
     * `true`, если сессия выбирает случайные элементы из списка
     * ([SessionPayload.Items], задача 2.1), а не числа из диапазона.
     */
    val isItemsMode: Boolean get() = payload is SessionPayload.Items

    /** `true`, если сессия бросает кубики ([SessionPayload.Dice], задача 2.2). */
    val isDiceMode: Boolean get() = payload is SessionPayload.Dice

    /** `true`, если сессия бросает монету ([SessionPayload.Coin], задача 2.3). */
    val isCoinMode: Boolean get() = payload is SessionPayload.Coin

    /** `true`, если сессия перемешивает список ([SessionPayload.Shuffle], задача 2.4). */
    val isShuffleMode: Boolean get() = payload is SessionPayload.Shuffle

    /** `true`, если сессия генерирует числа из диапазона ([SessionPayload.Numbers]). */
    val isNumbersMode: Boolean get() = payload is SessionPayload.Numbers

    /**
     * Список элементов режима [SessionPayload.Items] или
     * [SessionPayload.Shuffle] в порядке ввода (пустой для режима чисел).
     */
    val items: List<String> get() = when (val payload = payload) {
        is SessionPayload.Items -> payload.items
        is SessionPayload.Shuffle -> payload.items
        else -> emptyList()
    }

    // Часть параметров у двух режимов общая по смыслу, но разная по полям.
    // Для режима чисел это min/max, для списка — границы индексов `0..last`.
    // Благодаря этому генерация, прогресс и подсчёт «выбрано X из Y»
    // работают одинаково, а журнал остаётся списком Int (для списка — индексов).

    /** Нижняя граница: `min` диапазона, `0` для списка/монеты, `1` для кубика. */
    val min: Int get() = when (val payload = payload) {
        is SessionPayload.Numbers -> payload.min
        is SessionPayload.Items -> 0
        is SessionPayload.Dice -> 1
        is SessionPayload.Coin -> 0
        is SessionPayload.Shuffle -> 0
    }

    /** Верхняя граница: `max`, последний индекс списка, грани кубика, `1` у монеты. */
    val max: Int get() = when (val payload = payload) {
        is SessionPayload.Numbers -> payload.max
        is SessionPayload.Items -> payload.items.lastIndex
        is SessionPayload.Dice -> payload.sides
        is SessionPayload.Coin -> 1
        is SessionPayload.Shuffle -> payload.items.lastIndex
    }

    /** `true` — повторы разрешены (у кубиков, монеты и перемешивания — всегда). */
    val allowRepeats: Boolean get() = when (val payload = payload) {
        is SessionPayload.Numbers -> payload.allowRepeats
        is SessionPayload.Items -> payload.allowRepeats
        is SessionPayload.Dice -> true
        is SessionPayload.Coin -> true
        is SessionPayload.Shuffle -> true
    }

    /** История выданных значений: числа либо индексы элементов списка. */
    val generated: List<Int> get() = log.map { it.value }

    /** Последнее выданное значение (число либо индекс элемента) или `null`. */
    val lastNumber: Int? get() = log.lastOrNull()?.value

    /** Отображаемое значение записи журнала: число, элемент, кубик или сторона монеты. */
    fun displayOf(logValue: Int): String = displayOf(logValue, DefaultSessionTexts)

    /** Как [displayOf], но с локализованными метками (стороны монеты). */
    fun displayOf(logValue: Int, texts: SessionTexts): String = when (val payload = payload) {
        is SessionPayload.Numbers -> logValue.toString()
        is SessionPayload.Items -> payload.items.getOrElse(logValue) { "?" }
        is SessionPayload.Dice -> logValue.toString()
        is SessionPayload.Coin -> texts.coinSide(CoinSide.of(logValue))
        is SessionPayload.Shuffle -> payload.items.getOrElse(logValue) { "?" }
    }

    /** Сколько раз выпал орёл (режим [SessionPayload.Coin]). */
    val coinHeadsCount: Int get() = log.count { it.value == CoinSide.HEADS.ordinal }

    /** Сколько раз выпала решка (режим [SessionPayload.Coin]). */
    val coinTailsCount: Int get() = log.count { it.value == CoinSide.TAILS.ordinal }

    /**
     * Значения последнего полного броска кубиков (режим [SessionPayload.Dice]) —
     * последние [SessionPayload.Dice.count] записей журнала, либо `null`.
     */
    val lastDiceRoll: List<Int>? get() {
        val payload = payload as? SessionPayload.Dice ?: return null
        if (log.size < payload.count) return null
        return log.takeLast(payload.count).map { it.value }
    }

    /**
     * Элементы последней перестановки (режим [SessionPayload.Shuffle]) —
     * последние [SessionPayload.Shuffle.items] записей журнала, либо `null`,
     * если полной перестановки ещё не было.
     */
    val lastShuffle: List<String>? get() = lastShuffle(DefaultSessionTexts)

    /** Как [lastShuffle], но с локализованными метками. */
    fun lastShuffle(texts: SessionTexts): List<String>? {
        val payload = payload as? SessionPayload.Shuffle ?: return null
        if (log.size < payload.items.size) return null
        return log.takeLast(payload.items.size).map { displayOf(it.value, texts) }
    }

    /**
     * Последнее выданное значение, готовое к показу, или `null`.
     *
     * Для кубиков — сумма последнего броска; для перемешивания —
     * последняя перестановка через разделитель «→».
     */
    val lastDisplay: String? get() = lastDisplay(DefaultSessionTexts)

    /** Как [lastDisplay], но с локализованными метками. */
    fun lastDisplay(texts: SessionTexts): String? = when (val payload = payload) {
        is SessionPayload.Numbers -> log.lastOrNull()?.let { displayOf(it.value, texts) }
        is SessionPayload.Items -> log.lastOrNull()?.let { displayOf(it.value, texts) }
        is SessionPayload.Dice -> lastDiceRoll?.sum()?.toString()
        is SessionPayload.Coin -> log.lastOrNull()?.let { displayOf(it.value, texts) }
        is SessionPayload.Shuffle -> lastShuffle(texts)?.joinToString(SHUFFLE_SEPARATOR)
    }

    /** Вся история в порядке генерации, значения готовы к показу. */
    val generatedDisplay: List<String> get() = generatedDisplay(DefaultSessionTexts)

    /** Как [generatedDisplay], но с локализованными метками. */
    fun generatedDisplay(texts: SessionTexts): List<String> = log.map { displayOf(it.value, texts) }

    /** Размер множества значений: диапазон чисел, число элементов, грани кубика. */
    val rangeSize: Int by lazy {
        when (val payload = payload) {
            is SessionPayload.Numbers ->
                payload.size.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

            is SessionPayload.Items -> payload.items.size
            is SessionPayload.Dice -> payload.sides
            is SessionPayload.Coin -> 2
            is SessionPayload.Shuffle -> payload.items.size
        }
    }

    /**
     * Сколько различных чисел диапазона уже выбрано.
     *
     * Считается один раз на экземпляр (`Session` неизменяем): к списку
     * истории обращается каждый кадр UI, а полный `toSet()` на длинной
     * истории на каждом кадре — заметная лишняя работа.
     */
    val pickedCount: Int by lazy { log.mapTo(HashSet()) { it.value }.size }

    /** Сколько всего чисел выдано (с учётом повторов). */
    val generatedCount: Int get() = log.size

    /**
     * `true`, если в режиме «без повторений» выбраны все числа диапазона —
     * приложению нужно предложить новую сессию или сброс текущей.
     */
    val isExhausted: Boolean by lazy { !allowRepeats && pickedCount >= rangeSize }

    /**
     * Возвращает копию сессии с добавленным сгенерированным числом.
     *
     * @throws IllegalArgumentException если число вне диапазона
     * `min..max` (страховка: генератор диапазон уже гарантирует).
     */
    fun withGenerated(number: Int, at: Long): Session = withGenerated(listOf(number), at)

    /**
     * Возвращает копию сессии с добавленным батчем сгенерированных чисел.
     *
     * @throws IllegalArgumentException если какое-либо число вне диапазона
     * `min..max`.
     */
    fun withGenerated(numbers: List<Int>, at: Long): Session {
        require(numbers.all { it in min..max }) {
            val invalid = numbers.first { it !in min..max }
            "Число $invalid вне диапазона $min..$max"
        }
        return copy(
            log = log + numbers.map { GeneratedEntry(it, at) },
            lastUsedAt = at,
        )
    }

    /**
     * Возвращает копию сессии без последнего числа истории
     * («отменить последнее число»).
     *
     * Пустая история остаётся пустой.
     */
    fun withoutLastGenerated(at: Long): Session = copy(
        log = log.dropLast(1),
        lastUsedAt = at,
    )

    /**
     * Возвращает копию сессии без числа с индексом [index] в истории.
     *
     * @throws IllegalArgumentException если [index] вне границ истории.
     */
    fun removeGeneratedAt(index: Int, at: Long): Session {
        require(index in log.indices) {
            "Некорректный индекс: $index (размер истории ${log.size})"
        }
        return copy(
            log = log.filterIndexed { i, _ -> i != index },
            lastUsedAt = at,
        )
    }

    /**
     * Возвращает копию сессии с числом, добавленным вручную
     * (например, выпавшим вне приложения).
     *
     * @throws IllegalArgumentException если число вне диапазона
     * `min..max` либо уже есть в истории в режиме «без повторений».
     */
    fun addManual(number: Int, at: Long): Session {
        require(number in min..max) { "Число $number вне диапазона $min..$max" }
        require(allowRepeats || number !in generated) {
            "Число $number уже сгенерировано (режим «без повторений»)"
        }
        return withGenerated(number, at)
    }

    /** Возвращает копию сессии с очищенной историей чисел («скинуть» ранее выбранные). */
    fun withClearedNumbers(at: Long): Session = copy(
        log = emptyList(),
        lastUsedAt = at,
    )

    /**
     * Возвращает копию сессии с другим seed (для воспроизводимости
     * генерации) или без него (`null`).
     */
    fun withSeed(seed: Long?): Session = copy(seed = seed)

    /**
     * Возвращает копию сессии с новым [id], пустой историей и настройками
     * режима из оригинала (payload, seed, источник seed).
     *
     * Используется для быстрого повторения той же конфигурации:
     * «ещё один кубик», «ещё одна лотерея» и т. п.
     *
     * @param now время создания копии — попадает в [createdAt]/[lastUsedAt].
     */
    fun duplicate(now: Long, id: String = UUID.randomUUID().toString()): Session = copy(
        id = id,
        log = emptyList(),
        createdAt = now,
        lastUsedAt = now,
        // Новый экземпляр не наследует избранное/архив; теги — часть настроек.
        isFavorite = false,
        isArchived = false,
    )

    /** Возвращает копию сессии с флагом избранного [favorite]. */
    fun withFavorite(favorite: Boolean): Session = copy(isFavorite = favorite)

    /** Возвращает копию сессии с флагом архива [archived]. */
    fun withArchived(archived: Boolean): Session = copy(isArchived = archived)

    /**
     * Возвращает копию сессии с тегами [tags] в нормализованном виде:
     * без пробелов по краям, пустых значений и повторов.
     */
    fun withTags(tags: List<String>): Session = copy(
        tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )

    companion object {

        /** Разделитель элементов перестановки в [lastDisplay]. */
        private const val SHUFFLE_SEPARATOR = " → "

        /**
         * Создаёт новую сессию режима чисел.
         *
         * @param seed значение seed для воспроизводимой генерации.
         */
        fun new(
            min: Int,
            max: Int,
            allowRepeats: Boolean,
            now: Long,
            title: String = "$min..$max" + if (allowRepeats) "" else ", без повторов",
            seed: Long? = null,
            seedFromSecure: Boolean = false,
        ): Session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            min = min,
            max = max,
            allowRepeats = allowRepeats,
            log = emptyList(),
            createdAt = now,
            lastUsedAt = now,
            seed = seed,
            seedFromSecure = seedFromSecure,
        )

        /**
         * Создаёт новую сессию в заданном режиме [payload].
         *
         * Название по умолчанию — параметры режима; режимам-наследникам
         * стоит передавать осмысленное [title] явно.
         */
        fun new(
            payload: SessionPayload,
            now: Long,
            title: String = payload.toString(),
            seed: Long? = null,
            seedFromSecure: Boolean = false,
        ): Session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            payload = payload,
            log = emptyList(),
            createdAt = now,
            lastUsedAt = now,
            seed = seed,
            seedFromSecure = seedFromSecure,
        )
    }
}
