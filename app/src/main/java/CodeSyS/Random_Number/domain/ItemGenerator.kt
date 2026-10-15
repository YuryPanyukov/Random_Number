package CodeSyS.Random_Number.domain

import kotlin.random.Random

/**
 * Результат выбора случайных элементов из списка.
 *
 * - [Success] — выбран хотя бы один элемент; [Success.partial] = `true`,
 *   если запросили больше элементов, чем осталось (при «без повторений»);
 * - [Exhausted] — выбрать нечего: список пуст или все элементы уже выпали.
 */
sealed interface ItemGenerationResult {
    data class Success(
        /** Выпавшие элементы в порядке выбора. */
        val items: List<String>,
        /** Индексы выпавших элементов в исходном списке (идут в журнал). */
        val indices: List<Int>,
        val partial: Boolean = false,
    ) : ItemGenerationResult

    data object Exhausted : ItemGenerationResult
}

/**
 * Выбирает случайные элементы из пользовательского списка (задача 2.1).
 *
 * Логика выбора переиспользует [NumberGenerator] над индексами `0..items.lastIndex`:
 * это даёт и режим «без повторений» (уникальные индексы), и корректное
 * частичное исчерпание списка, не дублируя алгоритм.
 *
 * @param random источник случайности — вынесен в конструктор, чтобы тесты
 * подставляли детерминированный [Random] с фиксированным seed.
 */
class ItemGenerator(
    random: Random = Random.Default,
) {
    private val numbers = NumberGenerator(random)

    /**
     * Выбирает [count] элементов из [items].
     *
     * @param items список элементов (не пуст).
     * @param allowRepeats `true` — элементы могут повторяться; `false` —
     * индексы из [generated] не выдаются снова.
     * @param generated индексы уже выпавших элементов (только при
     * `allowRepeats = false`).
     *
     * @throws IllegalArgumentException если [items] пуст или `count < 1`.
     */
    fun next(
        count: Int,
        items: List<String>,
        allowRepeats: Boolean,
        generated: Collection<Int> = emptyList(),
    ): ItemGenerationResult {
        require(items.isNotEmpty()) { "Список элементов пуст" }

        val result = numbers.next(
            count = count,
            min = 0,
            max = items.lastIndex,
            allowRepeats = allowRepeats,
            generated = generated,
        )

        return when (result) {
            is BatchGenerationResult.Success -> ItemGenerationResult.Success(
                items = result.numbers.map { items[it] },
                indices = result.numbers,
                partial = result.partial,
            )

            BatchGenerationResult.Exhausted -> ItemGenerationResult.Exhausted
        }
    }
}
