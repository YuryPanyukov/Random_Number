package CodeSyS.Random_Number.domain

import CodeSyS.Random_Number.data.GeneratedEntry
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGeneratorTest {

    private fun numbers(
        min: Int = 1,
        max: Int = 10,
        allowRepeats: Boolean = true,
        generated: List<Int> = emptyList(),
    ) = Session(
        id = "n",
        title = "numbers",
        min = min,
        max = max,
        allowRepeats = allowRepeats,
        log = generated.map { GeneratedEntry(it, 0L) },
    )

    private fun items(
        allowRepeats: Boolean = false,
        generated: List<Int> = emptyList(),
    ) = Session(
        id = "i",
        title = "items",
        payload = SessionPayload.Items(
            items = listOf("Аня", "Борис", "Вера"),
            allowRepeats = allowRepeats,
        ),
        log = generated.map { GeneratedEntry(it, 0L) },
    )

    private fun generator(seed: Long = 42L) = SessionGenerator(
        numberGenerator = NumberGenerator(Random(seed)),
        itemGenerator = ItemGenerator(Random(seed)),
        diceRoller = DiceRoller(Random(seed)),
        coinFlipper = CoinFlipper(Random(seed)),
        shuffler = Shuffler(Random(seed)),
    )

    // --- Числа ---

    @Test
    fun `numbers - one value within the range`() {
        val result = generator().generate(numbers(min = 5, max = 7)) as SessionGenerationResult.Success

        assertEquals(1, result.values.size)
        assertTrue(result.values.single() in 5..7)
    }

    @Test
    fun `numbers without repeats - Exhausted when all picked`() {
        val result = generator().generate(
            numbers(min = 1, max = 2, allowRepeats = false, generated = listOf(1, 2)),
        )

        assertEquals(SessionGenerationResult.Exhausted, result)
    }

    @Test
    fun `numbers without repeats - skips already generated`() {
        val result = generator().generate(
            numbers(min = 1, max = 3, allowRepeats = false, generated = listOf(1, 2)),
        ) as SessionGenerationResult.Success

        assertEquals(listOf(3), result.values)
    }

    // --- Элементы ---

    @Test
    fun `items - returns an index within the list`() {
        val result = generator().generate(items()) as SessionGenerationResult.Success

        assertEquals(1, result.values.size)
        assertTrue(result.values.single() in 0..2)
    }

    @Test
    fun `items without repeats - Exhausted when all picked`() {
        val result = generator().generate(items(generated = listOf(0, 1, 2)))

        assertEquals(SessionGenerationResult.Exhausted, result)
    }

    // --- Кубики ---

    @Test
    fun `dice - returns one value per die`() {
        val session = Session(
            id = "d",
            title = "2d6",
            payload = SessionPayload.Dice(count = 3, sides = 6),
        )

        val result = generator().generate(session) as SessionGenerationResult.Success

        assertEquals(3, result.values.size)
        assertTrue(result.values.all { it in 1..6 })
    }

    // --- Монета ---

    @Test
    fun `coin - returns a single side value`() {
        val session = Session(id = "c", title = "coin", payload = SessionPayload.Coin)

        val result = generator().generate(session) as SessionGenerationResult.Success

        assertEquals(1, result.values.size)
        assertTrue(result.values.single() in 0..1)
    }

    // --- Перемешивание ---

    @Test
    fun `shuffle - returns a full permutation of indices`() {
        val session = Session(
            id = "sh",
            title = "shuffle",
            payload = SessionPayload.Shuffle(items = listOf("a", "b", "c", "d")),
        )

        val result = generator().generate(session) as SessionGenerationResult.Success

        assertEquals(4, result.values.size)
        assertEquals(setOf(0, 1, 2, 3), result.values.toSet())
    }

    // --- createSessionGenerator ---

    @Test
    fun `createSessionGenerator - seed makes the sequence reproducible`() {
        val a = createSessionGenerator(seed = 7L).generate(numbers(min = 1, max = 1000))
        val b = createSessionGenerator(seed = 7L).generate(numbers(min = 1, max = 1000))

        assertEquals(a, b)
    }

    @Test
    fun `createSessionGenerator - different seeds differ`() {
        val a = createSessionGenerator(seed = 1L).generate(numbers(min = 1, max = 1000))
        val b = createSessionGenerator(seed = 2L).generate(numbers(min = 1, max = 1000))

        assertTrue(a != b)
    }

    @Test
    fun `createSessionGenerator - null seed falls back`() {
        val fallback = generator(seed = 5L)

        val result = createSessionGenerator(seed = null, fallback = fallback)
            .generate(numbers(min = 1, max = 10))

        assertTrue(result is SessionGenerationResult.Success)
    }
}
