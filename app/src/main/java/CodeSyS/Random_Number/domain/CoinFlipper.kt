package CodeSyS.Random_Number.domain

import kotlin.random.Random

/**
 * Сторона монеты.
 *
 * [ordinal] используется как значение журнала сессии (0 — орёл, 1 — решка):
 * так режим монеты переиспользует общие механизмы истории, экспорта и
 * статистики, не вводя отдельный формат данных.
 *
 * @property label русское название стороны — фолбэк для
 * [CodeSyS.Random_Number.data.DefaultSessionTexts]. Локализованные названия
 * для истории/экспорта берутся из `SessionTexts` (см. `ResourceSessionTexts`).
 */
enum class CoinSide(val label: String) {
    /** Орёл. */
    HEADS("Орёл"),

    /** Решка. */
    TAILS("Решка"),
    ;

    companion object {

        /** Сторона по значению журнала (0 — орёл, 1 — решка). */
        fun of(logValue: Int): CoinSide = entries.getOrElse(logValue) { HEADS }
    }
}

/**
 * Бросок монеты.
 *
 * Один бросок даёт одну из двух сторон — по сути `NumberGenerator` над
 * диапазоном `0..1`, поэтому алгоритм выбора и seed общие с числами.
 *
 * @param random источник случайности — вынесен в конструктор для
 * детерминированных тестов.
 */
class CoinFlipper(
    random: Random = Random.Default,
) {
    private val numbers = NumberGenerator(random)

    /** Бросает монету один раз. */
    fun flip(): CoinSide = when (val result = numbers.next(min = 0, max = 1, allowRepeats = true)) {
        is GenerationResult.Success -> CoinSide.of(result.number)
        // Недостижимо: при разрешённых повторах исчерпание невозможно.
        GenerationResult.Exhausted -> CoinSide.HEADS
    }

    /**
     * Бросает монету [count] раз подряд.
     *
     * @throws IllegalArgumentException если `count < 1`.
     */
    fun flip(count: Int): List<CoinSide> {
        require(count >= 1) { "Некорректное количество бросков: count=$count" }
        return List(count) { flip() }
    }
}
