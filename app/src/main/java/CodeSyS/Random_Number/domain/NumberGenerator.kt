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
     * чисел), затем — точный перебор оставшихся значений.
     */
    private fun pickUnique(min: Int, max: Int, excluded: Set<Int>): Int? {
        if (excluded.isEmpty()) return nextInClosedRange(min, max)

        repeat(FAST_ATTEMPTS) {
            val candidate = nextInClosedRange(min, max)
            if (candidate !in excluded) return candidate
        }

        // Точный перебор: считаем доступные значения и выбираем n-й из них.
        var available = 0L
        for (value in min..max) {
            if (value !in excluded) available++
        }
        if (available == 0L) return null

        var target = random.nextLong(available)
        for (value in min..max) {
            if (value !in excluded) {
                if (target == 0L) return value
                target--
            }
        }
        return null // Недостижимо: target гарантированно найдёт значение.
    }

    private companion object {
        /** Число пробных «бросков» перед точным перебором диапазона. */
        const val FAST_ATTEMPTS = 128
    }
}
