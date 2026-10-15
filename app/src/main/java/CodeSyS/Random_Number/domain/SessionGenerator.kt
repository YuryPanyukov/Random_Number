package CodeSyS.Random_Number.domain

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import kotlin.random.Random

/**
 * Результат одной генерации в сессии любого режима.
 *
 * - [Success] — история сессии пополнена; [values] — то, что легло в журнал
 *   (числа, индексы элементов, значения кубиков, стороны монеты);
 * - [Exhausted] — в режиме «без повторений» выбирать больше нечего.
 */
sealed interface SessionGenerationResult {
    data class Success(val values: List<Int>) : SessionGenerationResult
    data object Exhausted : SessionGenerationResult
}

/**
 * Универсальный «шаг генерации» для сессии любого режима (виджет/плитка, 5.4).
 *
 * Сводит воедино логику, которая в `SessionViewModel` разложена по ветвям
 * (`generate`, `generateItem`, `generateDice`, `generateCoin`,
 * `generateShuffle`), но без Android и без ViewModel — чистый домен,
 * покрываемый unit-тестами. Из-за одного шага нажатие виджета и плитки
 * даёт ровно одно новое значение (перестановку — для перемешивания).
 *
 * @param numberGenerator генератор чисел — используется для режима чисел.
 * @param itemGenerator выбор элементов списка.
 * @param diceRoller бросок кубиков.
 * @param coinFlipper бросок монеты.
 * @param shuffler перемешивание списка.
 */
class SessionGenerator(
    private val numberGenerator: NumberGenerator = NumberGenerator(),
    private val itemGenerator: ItemGenerator = ItemGenerator(),
    private val diceRoller: DiceRoller = DiceRoller(),
    private val coinFlipper: CoinFlipper = CoinFlipper(),
    private val shuffler: Shuffler = Shuffler(),
) {

    /**
     * Генерирует один шаг для [session] и возвращает значения для журнала.
     *
     * При исчерпании (режим «без повторений», список) возвращает
     * [SessionGenerationResult.Exhausted] — вызывающий код не меняет сессию.
     */
    fun generate(session: Session): SessionGenerationResult = when (val payload = session.payload) {
        is SessionPayload.Numbers -> numbers(session, payload)
        is SessionPayload.Items -> items(session, payload)
        is SessionPayload.Dice -> dice(payload)
        is SessionPayload.Coin -> coin()
        is SessionPayload.Shuffle -> shuffle(payload)
    }

    private fun numbers(
        session: Session,
        payload: SessionPayload.Numbers,
    ): SessionGenerationResult =
        when (
            val result = numberGenerator.next(
                min = payload.min,
                max = payload.max,
                allowRepeats = payload.allowRepeats,
                generated = session.generated,
            )
        ) {
            is GenerationResult.Success -> SessionGenerationResult.Success(listOf(result.number))
            GenerationResult.Exhausted -> SessionGenerationResult.Exhausted
        }

    private fun items(
        session: Session,
        payload: SessionPayload.Items,
    ): SessionGenerationResult =
        when (
            val result = itemGenerator.next(
                count = 1,
                items = payload.items,
                allowRepeats = payload.allowRepeats,
                generated = session.generated,
            )
        ) {
            is ItemGenerationResult.Success -> SessionGenerationResult.Success(result.indices)
            ItemGenerationResult.Exhausted -> SessionGenerationResult.Exhausted
        }

    private fun dice(payload: SessionPayload.Dice): SessionGenerationResult {
        val roll = diceRoller.roll(count = payload.count, sides = payload.sides)
        return SessionGenerationResult.Success(roll.dice)
    }

    private fun coin(): SessionGenerationResult =
        SessionGenerationResult.Success(listOf(coinFlipper.flip().ordinal))

    private fun shuffle(payload: SessionPayload.Shuffle): SessionGenerationResult =
        SessionGenerationResult.Success(shuffler.shuffle(payload.items.indices.toList()))
}

/**
 * Создаёт генератор, следующий правилам сессии: при заданном seed
 * последовательность воспроизводима, иначе используется [fallback].
 *
 * Общий для UI и для виджета/плитки, чтобы «одно нажатие» в любом месте
 * приложения вело себя одинаково.
 */
fun createSessionGenerator(
    seed: Long?,
    randomMode: RandomSourceMode = RandomSourceMode.DEFAULT,
    fallback: SessionGenerator = SessionGenerator(),
): SessionGenerator {
    if (seed == null) return fallback
    val random: Random = createRandom(randomMode, seed)
    return SessionGenerator(
        numberGenerator = NumberGenerator(random),
        itemGenerator = ItemGenerator(random),
        diceRoller = DiceRoller(random),
        coinFlipper = CoinFlipper(random),
        shuffler = Shuffler(random),
    )
}
