package CodeSyS.Random_Number.domain

import kotlin.random.Random

/**
 * Результат одного броска набора кубиков.
 *
 * @property dice выпавшие значения (размер = числу кубиков).
 * @property sides число граней кубика (для отображения «2d6» и т.п.).
 */
data class DiceRoll(
    val dice: List<Int>,
    val sides: Int,
) {
    init {
        require(dice.isNotEmpty()) { "Пустой бросок" }
        require(dice.all { it in 1..sides }) { "Значение кубика вне 1..$sides: $dice" }
    }

    /** Сумма всех выпавших значений. */
    val sum: Int get() = dice.sum()

    /** Краткое обозначение броска: «2d6». */
    val label: String get() = "${dice.size}d$sides"

    companion object {

        /** Значение отдельного кубика — для отображения «4 + 2 + 6». */
        fun formatDetail(dice: List<Int>): String = dice.joinToString(" + ")
    }
}

/**
 * Бросок кубиков: [count] кубиков с [sides] гранями за один вызов.
 *
 * Значение каждого кубика — отдельный бросок `Random`; при заданном seed
 * последовательность воспроизводима, как и для чисел.
 *
 * @param random источник случайности — вынесен в конструктор,
 * чтобы тесты могли подставить детерминированный [Random].
 */
class DiceRoller(
    private val random: Random = Random.Default,
) {

    /**
     * Бросает [count] кубиков с [sides] гранями.
     *
     * @throws IllegalArgumentException если `count < 1` или `sides < 2`.
     */
    fun roll(count: Int, sides: Int): DiceRoll {
        require(count >= 1) { "Некорректное количество кубиков: count=$count" }
        require(sides >= 2) { "У кубика минимум две грани: sides=$sides" }
        return DiceRoll(dice = List(count) { random.nextInt(1, sides + 1) }, sides = sides)
    }
}
