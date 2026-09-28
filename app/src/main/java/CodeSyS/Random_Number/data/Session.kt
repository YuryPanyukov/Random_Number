package CodeSyS.Random_Number.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Сохранённая сессия генерации случайных чисел.
 *
 * @param id уникальный идентификатор сессии.
 * @param title человекочитаемое имя (например, «1..100, без повторов»).
 * @param min нижняя граница диапазона (включительно).
 * @param max верхняя граница диапазона (включительно).
 * @param allowRepeats `true` — повторы разрешены; `false` — уже
 * сгенерированные числа не должны появляться снова.
 * @param generated история выданных чисел (в порядке генерации).
 * @param createdAt время создания сессии (epoch millis).
 * @param lastUsedAt время последнего использования (epoch millis),
 * сессии сортируются по убыванию этого поля.
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    val min: Int,
    val max: Int,
    val allowRepeats: Boolean,
    val generated: List<Int> = emptyList(),
    val createdAt: Long = 0L,
    val lastUsedAt: Long = createdAt,
) {
    init {
        require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }
    }

    /** Размер диапазона чисел (включительно). */
    val rangeSize: Int
        get() = (max.toLong() - min.toLong() + 1L)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()

    /** Сколько различных чисел диапазона уже выбрано. */
    val pickedCount: Int
        get() = generated.toSet().size

    /**
     * `true`, если в режиме «без повторений» выбраны все числа диапазона —
     * приложению нужно предложить новую сессию или сброс текущей.
     */
    val isExhausted: Boolean
        get() = !allowRepeats && pickedCount >= rangeSize

    /** Возвращает копию сессии с добавленным сгенерированным числом. */
    fun withGenerated(number: Int, at: Long): Session = copy(
        generated = generated + number,
        lastUsedAt = at,
    )

    /** Возвращает копию сессии с очищенной историей чисел («скинуть» ранее выбранные). */
    fun withClearedNumbers(at: Long): Session = copy(
        generated = emptyList(),
        lastUsedAt = at,
    )

    companion object {
        fun new(
            min: Int,
            max: Int,
            allowRepeats: Boolean,
            now: Long,
            title: String = "$min..$max" + if (allowRepeats) "" else ", без повторов",
        ): Session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            min = min,
            max = max,
            allowRepeats = allowRepeats,
            generated = emptyList(),
            createdAt = now,
            lastUsedAt = now,
        )
    }
}
