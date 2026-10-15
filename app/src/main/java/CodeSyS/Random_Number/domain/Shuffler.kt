package CodeSyS.Random_Number.domain

import kotlin.random.Random

/**
 * Перемешивание списка (задача 2.4).
 *
 * Реализует тасование Фишера–Йетса (Fisher–Yates): каждая перестановка
 * равновероятна, сложность — O(n). Источник случайности внедряется
 * в конструктор, поэтому с фиксированным seed порядок воспроизводим,
 * как и у остальных режимов.
 *
 * @param random источник случайности — для детерминированных тестов.
 */
class Shuffler(
    private val random: Random = Random.Default,
) {

    /**
     * Возвращает новую перестановку [items] (исходный список не меняется).
     *
     * @throws IllegalArgumentException если [items] пуст.
     */
    fun <T> shuffle(items: List<T>): List<T> {
        require(items.isNotEmpty()) { "Список для перемешивания пуст" }

        val result = items.toMutableList()
        // Идём от конца к началу: элемент i меняется местами со случайным
        // из [0, i]. К моменту обработки i «хвост» уже перемешан.
        for (i in result.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }
}
