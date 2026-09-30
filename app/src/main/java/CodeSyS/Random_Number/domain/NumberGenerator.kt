package CodeSyS.Random_Number.domain

import kotlin.random.Random

/**
 * Результат одной попытки сгенерировать число.
 *
 * - [Success] — число сгенерировано;
 * - [Exhausted] — в режиме «без повторений» выбраны все числа диапазона.
 */
sealed interface GenerationResult {
    data class Success(val number: Int) : GenerationResult
    data object Exhausted : GenerationResult
}

/**
 * Результат батч-генерации ([NumberGenerator.next] с параметром `count`).
 *
 * - [BatchGenerationResult.Success] — сгенерировано хотя бы одно число;
 *   [BatchGenerationResult.partial] = `true`, если запросить чисел
 *   больше, чем осталось в диапазоне (частичное исчерпание) —
 *   в [BatchGenerationResult.numbers] окажутся только оставшиеся;
 * - [BatchGenerationResult.Exhausted] — не удалось сгенерировать
 *   ни одного числа (диапазон «без повторений» полностью исчерпан).
 */
sealed interface BatchGenerationResult {
    data class Success(
        val numbers: List<Int>,
        val partial: Boolean = false,
    ) : BatchGenerationResult

    data object Exhausted : BatchGenerationResult
}

/**
 * Генератор случайных чисел в диапазоне `[min, max]` (включительно).
 *
 * @param random источник случайности — вынесен в конструктор,
 * чтобы тесты могли подставить детерминированный [Random] с фиксированным seed.
 */
class NumberGenerator(
    private val random: Random = Random.Default,
) {

    /**
     * Генерирует следующее число.
     *
     * @param min нижняя граница диапазона (включительно).
     * @param max верхняя граница диапазона (включительно).
     * @param allowRepeats `true` — повторы разрешены; `false` — ранее
     * сгенерированные числа из [generated] не выдаются повторно.
     * @param generated числа, уже выданные в текущей сессии (используются
     * только при `allowRepeats = false`).
     *
     * @throws IllegalArgumentException если `min > max`.
     */
    fun next(
        min: Int,
        max: Int,
        allowRepeats: Boolean,
        generated: Collection<Int> = emptyList(),
    ): GenerationResult {
        require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }

        if (allowRepeats) {
            return GenerationResult.Success(nextInClosedRange(min, max))
        }

        val excluded = generated.toHashSet()
        val number = pickUnique(min, max, excluded)
            ?: return GenerationResult.Exhausted
        return GenerationResult.Success(number)
    }

    /**
     * Генерирует батч из [count] чисел.
     *
     * В режиме «без повторений» внутри батча повторов нет, а при
     * частичном исчерпании возвращаются только оставшиеся числа
     * ([BatchGenerationResult.Success] с `partial = true`).
     * Если не осталось ни одного числа — [BatchGenerationResult.Exhausted].
     *
     * @throws IllegalArgumentException если `min > max` или `count < 1`.
     */
    fun next(
        count: Int,
        min: Int,
        max: Int,
        allowRepeats: Boolean,
        generated: Collection<Int> = emptyList(),
    ): BatchGenerationResult {
        require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }
        require(count >= 1) { "Некорректное количество: count=$count" }

        if (allowRepeats) {
            return BatchGenerationResult.Success(
                numbers = List(count) { nextInClosedRange(min, max) },
                partial = false,
            )
        }

        val excluded = generated.toHashSet()
        val numbers = ArrayList<Int>(count)
        repeat(count) {
            val number = pickUnique(min, max, excluded)
                ?: return if (numbers.isEmpty()) {
                    BatchGenerationResult.Exhausted
                } else {
                    BatchGenerationResult.Success(numbers, partial = true)
                }
            numbers += number
            excluded += number
        }
        return BatchGenerationResult.Success(numbers, partial = false)
    }

    /** Случайное число из закрытого диапазона `[min, max]`. */
    private fun nextInClosedRange(min: Int, max: Int): Int =
        if (max == Int.MAX_VALUE) {
            // nextInt(min, max + 1) переполнился бы на max == Int.MAX_VALUE.
            random.nextLong(min.toLong(), max.toLong() + 1L).toInt()
        } else {
            random.nextInt(min, max + 1)
        }

    /**
     * Возвращает случайное число из `[min, max]`, которого нет в [excluded],
     * либо `null`, если все числа диапазона исчерпаны.
     *
     * Сначала делается несколько «бросков» (быстро, пока исключено мало
     * чисел). Затем — выбор из свободных значений: из диапазона берётся
     * случайный сдвиг, и значения просматриваются по кругу от него, пока
     * не встретится не-исключённое.
     *
     * Такой обход посещает ровно столько значений, сколько занято
     * «хвостом» до первого свободного, и не зависит от размера
     * диапазона (в отличие от полного перебора `min..max`).
     */
    private fun pickUnique(min: Int, max: Int, excluded: Set<Int>): Int? {
        if (excluded.isEmpty()) return nextInClosedRange(min, max)

        repeat(FAST_ATTEMPTS) {
            val candidate = nextInClosedRange(min, max)
            if (candidate !in excluded) return candidate
        }

        val size = rangeSizeOf(min, max)
        val available = size - excluded.count { it in min..max }
        if (available <= 0L) return null

        // Случайный сдвиг внутри диапазона: свободные значения в среднем
        // на расстоянии size / available от старта, а не от его начала.
        var offset = random.nextLong(size)
        var visited = 0L
        while (visited < size) {
            val value = valueAtOffset(min, offset)
            if (value !in excluded) return value
            offset = if (offset + 1 == size) 0 else offset + 1
            visited++
        }
        return null // Недостижимо: available > 0 гарантирует свободное значение.
    }

    /** Размер диапазона (может перекрывать весь Int, поэтому Long). */
    private fun rangeSizeOf(min: Int, max: Int): Long = max.toLong() - min.toLong() + 1L

    /** Значение диапазона по смещению [offset] от [min] (по кругу). */
    private fun valueAtOffset(min: Int, offset: Long): Int = (min.toLong() + offset).toInt()

    private companion object {
        /** Число пробных «бросков» перед обходом диапазона. */
        const val FAST_ATTEMPTS = 128
    }
}
