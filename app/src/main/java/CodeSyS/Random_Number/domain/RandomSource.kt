package CodeSyS.Random_Number.domain

import java.security.SecureRandom
import kotlin.random.Random

/**
 * Источник случайности для генератора.
 *
 * Вынесен в отдельную фабрику, чтобы можно было выбрать режим
 * в настройках: обычный [Random.Default] (быстро) или
 * [SecureRandom]-обёртка (медленнее, но устойчивее к предсказанию).
 */
enum class RandomSourceMode {
    /** Обычный ГПСЧ Kotlin — достаточно для бытовых задач. */
    DEFAULT,

    /** Криптографический ГПСЧ — для розыгрышей и жеребьёвок. */
    SECURE,
}

/**
 * Создаёт [Random] для [NumberGenerator].
 *
 * @param mode источник случайности.
 * @param seed если задан — генератор детерминирован: один и тот же seed
 * даёт одну и ту же последовательность чисел (для проверки розыгрышей).
 * @return генератор; при `seed != null` значение [Random] кешируется,
 * иначе создаётся новый на каждый вызов.
 */
fun createRandom(mode: RandomSourceMode, seed: Long?): Random = when {
    seed != null -> Random(seed)

    mode == RandomSourceMode.SECURE -> SecureRandomAdapter(SecureRandom())

    else -> Random.Default
}

/**
 * Адаптер [SecureRandom] к [kotlin.random.Random].
 *
 * [SecureRandom] не является [Random], поэтому используется только
 * `nextInt()`; остальные методы базового класса [Random] работают
 * через него.
 *
 * Важно: перегрузки `SecureRandom.nextInt(from, until)` и
 * `nextLong(from, until)` появились только в Java 17 (Android API 35),
 * поэтому диапазонные методы реализованы вручную поверх
 * `nextInt(until)` / `nextLong()` — иначе приложение падало бы
 * на устройствах со старым SDK.
 */
private class SecureRandomAdapter(
    private val secure: SecureRandom,
) : Random() {

    override fun nextBits(bitCount: Int): Int = secure.nextInt()

    override fun nextInt(until: Int): Int = secure.nextInt(until)

    override fun nextInt(from: Int, until: Int): Int =
        from + nextBoundedLong(until.toLong() - from.toLong()).toInt()

    override fun nextLong(): Long = secure.nextLong()

    override fun nextLong(from: Long, until: Long): Long = from + nextBoundedLong(until - from)

    override fun nextDouble(): Double = secure.nextDouble()

    /**
     * Равномерное значение в `[0, bound)` для `bound > 0`.
     *
     * Для коротких диапазонов (помещающихся в [Int]) хватает одного
     * [SecureRandom.nextInt]. Для широких используется отбор с отбрасыванием
     * «хвоста» 64-битного диапазона, иначе остаток был бы смещён.
     *
     * Ширина диапазона считается в [Long]: в [Int] она переполняется
     * для диапазонов вроде `Int.MIN_VALUE..Int.MAX_VALUE`.
     */
    private fun nextBoundedLong(bound: Long): Long {
        require(bound > 0L) { "Некорректная граница: bound=$bound" }
        if (bound <= Int.MAX_VALUE) return secure.nextInt(bound.toInt()).toLong()

        val range = bound.toULong()
        // Порог отбрасываемого «хвоста»: беззнаковое вычитание 0 - range
        // даёт 2^64 - range, а остаток от деления на range — 2^64 mod range.
        // Значения ниже порога отбрасываем, иначе остаток был бы смещён.
        val threshold = (0UL - range) % range
        var candidate = secure.nextLong().toULong()
        while (candidate < threshold) {
            candidate = secure.nextLong().toULong()
        }
        return (candidate % range).toLong()
    }
}
