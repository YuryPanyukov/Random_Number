package CodeSyS.Random_Number.domain

/**
 * Готовый шаблон диапазона для быстрого создания сессии в один тап.
 *
 * Названия пресетов не хранятся здесь: тексты лежат в ресурсах
 * (`res/values/strings.xml`), чтобы поддерживать i18n, а enum —
 * чистый доменный тип.
 *
 * @param min нижняя граница диапазона (включительно).
 * @param max верхняя граница диапазона (включительно).
 * @param allowRepeats значение переключателя «Без повторений»
 * (`false` — включён) при выборе пресета.
 */
enum class RangePreset(
    val min: Int,
    val max: Int,
    val allowRepeats: Boolean,
) {
    /** «Кубик 1–6» — несколько бросков подряд, повторы разрешены. */
    DICE(min = 1, max = 6, allowRepeats = true),

    /** «Монета 0–1» — орёл/решка. */
    COIN(min = 0, max = 1, allowRepeats = true),

    /** «Лотерея 1–45» — классическая лотерея без повторов. */
    LOTTERY(min = 1, max = 45, allowRepeats = false),

    /** «Сотня 1–100» — диапазон по умолчанию, без повторов. */
    HUNDRED(min = 1, max = 100, allowRepeats = false),
}
