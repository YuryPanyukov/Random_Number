package CodeSyS.Random_Number.domain

import kotlin.math.roundToInt

/**
 * Сводная статистика по истории генерации.
 *
 * Считается чистой функцией над списком чисел — покрывается
 * unit-тестами без Android.
 *
 * @param count сколько чисел всего выдано.
 * @param unique сколько различных чисел выдано.
 * @param min минимальное число или `null`, если история пуста.
 * @param max максимальное число или `null`, если история пуста.
 * @param average среднее арифметическое (округлено до сотых) или `null`.
 * @param median медиана или `null`, если история пуста.
 * @param mostFrequent самое частое число и его количество (по убыванию частоты,
 * при равенстве — меньшее число).
 * @param leastFrequent самое редкое число и его количество.
 * @param duplicates сколько чисел выдавалось повторно (`count - unique`).
 */
data class SessionStats(
    val count: Int,
    val unique: Int,
    val min: Int?,
    val max: Int?,
    val average: Double?,
    val median: Double?,
    val mostFrequent: Frequent?,
    val leastFrequent: Frequent?,
    val duplicates: Int,
) {
    /** Значение и число его появлений. */
    data class Frequent(val value: Int, val count: Int)

    companion object {
        val EMPTY = SessionStats(
            count = 0,
            unique = 0,
            min = null,
            max = null,
            average = null,
            median = null,
            mostFrequent = null,
            leastFrequent = null,
            duplicates = 0,
        )
    }
}

/**
 * Считает статистику по списку сгенерированных чисел [values].
 *
 * Пустой список даёт [SessionStats.EMPTY].
 */
fun computeSessionStats(values: List<Int>): SessionStats {
    if (values.isEmpty()) return SessionStats.EMPTY

    val counts = values.groupingBy { it }.eachCount()
    val sorted = values.sorted()
    val sum = values.sumOf { it.toLong() }

    return SessionStats(
        count = values.size,
        unique = counts.size,
        min = sorted.first(),
        max = sorted.last(),
        average = round2(sum.toDouble() / values.size),
        median = medianOf(sorted),
        mostFrequent = extremeByCount(counts, most = true),
        leastFrequent = extremeByCount(counts, most = false),
        duplicates = values.size - counts.size,
    )
}

/** Медиана: среднее двух центральных значений при чётном количестве. */
private fun medianOf(sorted: List<Int>): Double {
    val size = sorted.size
    val middle = size / 2
    return if (size % 2 == 1) {
        sorted[middle].toDouble()
    } else {
        (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2
    }
}

/**
 * Самое частое (или самое редкое) число.
 *
 * При равенстве счётчиков выбирается меньшее число — результат
 * детерминирован и не «прыгает» между перерисовками.
 */
private fun extremeByCount(
    counts: Map<Int, Int>,
    most: Boolean,
): SessionStats.Frequent {
    val best = counts.entries.minWithOrNull(
        compareBy<Map.Entry<Int, Int>> { if (most) -it.value else it.value }.thenBy { it.key },
    ) ?: error("Список значений пуст (extremeByCount вызван для пустой карты)")
    return SessionStats.Frequent(best.key, best.value)
}

/** Округление до сотых. */
private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0
