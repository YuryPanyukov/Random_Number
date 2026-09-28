package CodeSyS.Random_Number.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NumberGeneratorTest {

    private fun generator(seed: Long = 42L) = NumberGenerator(Random(seed))

    // --- Повторы разрешены ---

    @Test
    fun `repeats allowed - all values stay in range`() {
        val generator = generator()
        repeat(1_000) {
            val result = generator.next(min = -5, max = 5, allowRepeats = true)
            result as GenerationResult.Success
            assertTrue("число ${result.number} вне диапазона -5..5", result.number in -5..5)
        }
    }

    @Test
    fun `repeats allowed - never exhausted even for tiny range`() {
        val generator = generator()
        repeat(10_000) {
            val result = generator.next(min = 1, max = 2, allowRepeats = true)
            assertTrue("ожидался Success, получен $result", result is GenerationResult.Success)
        }
    }

    @Test
    fun `repeats allowed - ignores previously generated`() {
        val generator = generator()
        val result = generator.next(
            min = 1,
            max = 3,
            allowRepeats = true,
            generated = listOf(1, 2, 3),
        )
        assertTrue(result is GenerationResult.Success)
    }

    // --- Без повторений ---

    @Test
    fun `no repeats - generates all unique values for range 1-10`() {
        val generator = generator()
        val generated = mutableListOf<Int>()
        repeat(10) {
            val result = generator.next(min = 1, max = 10, allowRepeats = false, generated = generated)
            result as GenerationResult.Success
            assertTrue("число ${result.number} уже выдавалось", result.number !in generated)
            generated += result.number
        }
        assertEquals(10, generated.toSet().size)
        assertEquals((1..10).toSet(), generated.toSet())
    }

    @Test
    fun `no repeats - returns exhausted when all numbers picked`() {
        val generator = generator()
        val all = (1..10).toList()
        val result = generator.next(min = 1, max = 10, allowRepeats = false, generated = all)
        assertEquals(GenerationResult.Exhausted, result)
    }

    @Test
    fun `no repeats - returns remaining single number`() {
        val generator = generator()
        val generated = (1..9).toList()
        val result = generator.next(min = 1, max = 10, allowRepeats = false, generated = generated)
        result as GenerationResult.Success
        assertEquals(10, result.number)
    }

    @Test
    fun `no repeats - large range skips generated subset`() {
        val generator = generator()
        val generated = (1_000..1_100).toList()
        repeat(200) {
            val result = generator.next(min = 1, max = 2_000, allowRepeats = false, generated = generated)
            result as GenerationResult.Success
            assertTrue("выдано исключённое число ${result.number}", result.number !in generated)
            assertTrue(result.number in 1..2_000)
        }
    }

    @Test
    fun `no repeats - full sweep of 0-100 yields all unique then exhausted`() {
        val generator = generator(seed = 1234L)
        val generated = mutableListOf<Int>()

        // 101 число диапазона 0..100: каждое выдаётся ровно один раз.
        repeat(101) {
            val result = generator.next(min = 0, max = 100, allowRepeats = false, generated = generated)
            result as GenerationResult.Success
            assertTrue("число ${result.number} повторилось", result.number !in generated)
            generated += result.number
        }

        assertEquals(101, generated.toSet().size)
        assertEquals((0..100).toSet(), generated.toSet())

        // После последнего числа — новых чисел не генерируется.
        repeat(5) {
            val result = generator.next(min = 0, max = 100, allowRepeats = false, generated = generated)
            assertEquals(GenerationResult.Exhausted, result)
        }
        assertEquals(101, generated.size)
    }

    // --- Граничные случаи ---

    @Test
    fun `single value range - first succeeds, then exhausted`() {
        val generator = generator()
        val first = generator.next(min = 7, max = 7, allowRepeats = false)
        assertEquals(GenerationResult.Success(7), first)

        val second = generator.next(min = 7, max = 7, allowRepeats = false, generated = listOf(7))
        assertEquals(GenerationResult.Exhausted, second)
    }

    @Test
    fun `full int range - works without overflow`() {
        val generator = generator()
        val result = generator.next(min = Int.MIN_VALUE, max = Int.MAX_VALUE, allowRepeats = true)
        assertTrue(result is GenerationResult.Success)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid range - min greater than max throws`() {
        generator().next(min = 10, max = 1, allowRepeats = true)
    }

    // --- Детерминизм ---

    /** Генерирует [count] уникальных чисел одним генератором с данным seed. */
    private fun uniqueSequence(seed: Long, count: Int): List<Int> {
        val generator = generator(seed)
        val generated = mutableListOf<Int>()
        return buildList {
            while (size < count) {
                val result = generator.next(min = 1, max = 20, allowRepeats = false, generated = generated)
                result as GenerationResult.Success
                add(result.number)
                generated += result.number
            }
        }
    }

    @Test
    fun `same seed - produces identical sequences with repeats`() {
        val genA = generator(1L)
        val genB = generator(1L)
        val seqA = List(50) { genA.next(1, 100, allowRepeats = true) }
        val seqB = List(50) { genB.next(1, 100, allowRepeats = true) }
        assertEquals(seqA, seqB)
    }

    @Test
    fun `same seed - produces identical sequences without repeats`() {
        val seqA = uniqueSequence(seed = 7L, count = 5)
        val seqB = uniqueSequence(seed = 7L, count = 5)

        assertEquals(seqA, seqB)
        // И уникальность, и диапазон при этом сохраняются.
        assertEquals(5, seqA.toSet().size)
        assertTrue(seqA.all { it in 1..20 })
    }

    @Test
    fun `different seeds - produce different sequences`() {
        val seqA = uniqueSequence(seed = 1L, count = 10)
        val seqB = uniqueSequence(seed = 2L, count = 10)
        assertTrue(seqA != seqB)
    }
}
