package CodeSyS.Random_Number.data

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
    ) : this(
        id = id,
        title = title,
        payload = SessionPayload.Numbers(min = min, max = max, allowRepeats = allowRepeats),
        log = log,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        seed = seed,
    )

    /**
     * Параметры режима чисел.
     *
     * Пока режим один, payload всегда [SessionPayload.Numbers], поэтому
     * здесь `Numbers` без обёртки: свойства `min`/`max`/`allowRepeats`
     * и весь существующий код остались плоскими. Когда появятся другие
     * режимы, каждый из них даст свою реализацию этих параметров
     * (sealed-полиморфизм вместо `when` по флагу), а обращения к числовым
     * полям останутся только в коде режима чисел.
     */
    private val numbers: SessionPayload.Numbers
        get() = payload as SessionPayload.Numbers

    // Делегирование параметров режима чисел: код и данные режима «числа»
    // остались плоскими (session.min, session.allowRepeats, …).

    /** Нижняя граница диапазона (режим чисел). */
    val min: Int get() = numbers.min

    /** Верхняя граница диапазона (режим чисел). */
    val max: Int get() = numbers.max

    /** `true` — повторы разрешены (режим чисел). */
    val allowRepeats: Boolean get() = numbers.allowRepeats

    init {
        // Параметры режима читаются напрямую; при добавлении нового режима
        // это станет полиморфным доступом через SessionPayload.
        payload as SessionPayload.Numbers
    }

    /** История выданных чисел (в порядке генерации). */
    val generated: List<Int> get() = log.map { it.value }

    /** Последнее выданное число или `null`. */
    val lastNumber: Int? get() = log.lastOrNull()?.value

    /** Размер диапазона чисел (включительно). */
    val rangeSize: Int by lazy {
        numbers.size.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
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

    companion object {

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
        ): Session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            payload = payload,
            log = emptyList(),
            createdAt = now,
            lastUsedAt = now,
            seed = seed,
        )
    }
}
