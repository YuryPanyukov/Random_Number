package CodeSyS.Random_Number.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    // --- Батч-генерация (1.1) ---

    @Test
    fun `batch with repeats - returns requested count within range`() {
        val result = generator().next(count = 5, min = 1, max = 10, allowRepeats = true)
        result as BatchGenerationResult.Success
        assertEquals(5, result.numbers.size)
        assertFalse(result.partial)
        assertTrue(result.numbers.all { it in 1..10 })
    }

    @Test
    fun `batch without repeats - all unique and skipping generated`() {
        val generator = generator()
        val generated = (1..5).toList()
        val result = generator.next(
            count = 10,
            min = 1,
            max = 20,
            allowRepeats = false,
            generated = generated,
        )
        result as BatchGenerationResult.Success
        assertEquals(10, result.numbers.size)
        assertFalse(result.partial)
        assertEquals(10, result.numbers.toSet().size)
        assertTrue(result.numbers.none { it in generated })
        assertTrue(result.numbers.all { it in 1..20 })
    }

    @Test
    fun `batch without repeats - partial exhaustion returns only remaining`() {
        val generator = generator()
        val result = generator.next(
            count = 5,
            min = 1,
            max = 10,
            allowRepeats = false,
            generated = (1..8).toList(),
        )
        result as BatchGenerationResult.Success
        assertTrue("ожидался частичный батч", result.partial)
        assertEquals(setOf(9, 10), result.numbers.toSet())
    }

    @Test
    fun `batch without repeats - fully exhausted returns Exhausted`() {
        val result = generator().next(
            count = 3,
            min = 1,
            max = 10,
            allowRepeats = false,
            generated = (1..10).toList(),
        )
        assertEquals(BatchGenerationResult.Exhausted, result)
    }

    @Test
    fun `batch with repeats - never partial even when range is exhausted`() {
        val result = generator().next(
            count = 7,
            min = 1,
            max = 1,
            allowRepeats = true,
            generated = listOf(1),
        )
        result as BatchGenerationResult.Success
        assertEquals(List(7) { 1 }, result.numbers)
        assertFalse(result.partial)
    }

    @Test
    fun `batch - same seed produces identical batches`() {
        val a = generator(5L).next(count = 8, min = 1, max = 50, allowRepeats = false)
        val b = generator(5L).next(count = 8, min = 1, max = 50, allowRepeats = false)
        assertEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `batch - zero count throws`() {
        generator().next(count = 0, min = 1, max = 10, allowRepeats = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `batch - invalid range throws`() {
        generator().next(count = 2, min = 10, max = 1, allowRepeats = true)
    }

    // --- Большие диапазоны: обход вместо полного перебора (P1.1) ---

    @Test(timeout = 5_000)
    fun `no repeats - huge range with exclusions at the range start`() {
        val generator = generator()
        val min = 1
        val max = 100_000_000
        // Все исключения в начале диапазона: линейный перебор от `min`
        // проходил бы весь диапазон, круговой обход — короткий участок.
        val generated = (min..50_000).toList()

        val result = generator.next(min = min, max = max, allowRepeats = false, generated = generated)

        result as GenerationResult.Success
        assertTrue(result.number !in generated)
        assertTrue(result.number in min..max)
    }

    @Test(timeout = 5_000)
    fun `no repeats - full int range works with exclusions at the range start`() {
        val generator = generator()
        val generated = (Int.MIN_VALUE..Int.MIN_VALUE + 50_000).toList()

        val result = generator.next(
            min = Int.MIN_VALUE,
            max = Int.MAX_VALUE,
            allowRepeats = false,
            generated = generated,
        )

        result as GenerationResult.Success
        assertTrue(result.number !in generated)
    }

    @Test
    fun `no repeats - only the range boundaries are excluded`() {
        val generator = generator()
        val result = generator.next(
            min = 1,
            max = 3,
            allowRepeats = false,
            generated = listOf(1, 3),
        )

        assertEquals(GenerationResult.Success(2), result)
    }

    @Test(timeout = 5_000)
    fun `no repeats - dense exclusions force the fallback walk`() {
        val generator = generator(seed = 7L)
        val max = 200_000
        // Занято 95% диапазона: «быстрые броски» почти наверняка не сработают,
        // и выбирать придётся обходом по кругу.
        val generated = (1..190_000).toList()

        repeat(50) {
            val result = generator.next(min = 1, max = max, allowRepeats = false, generated = generated)
            result as GenerationResult.Success
            assertTrue("выдано исключённое число ${result.number}", result.number !in generated)
            assertTrue(result.number in 1..max)
        }
    }

    @Test
    fun `no repeats - all values excluded returns Exhausted`() {
        val generator = generator()
        val result = generator.next(
            min = 1,
            max = 1_000,
            allowRepeats = false,
            generated = (1..1_000).toList(),
        )

        assertEquals(GenerationResult.Exhausted, result)
    }

    @Test(timeout = 5_000)
    fun `no repeats - batch on huge range returns only available values`() {
        val generator = generator()
        val generated = (1..99).toList()

        val result = generator.next(
            count = 10,
            min = 1,
            max = 1_000_000_000,
            allowRepeats = false,
            generated = generated,
        )

        result as BatchGenerationResult.Success
        assertEquals(10, result.numbers.size)
        assertEquals(10, result.numbers.toSet().size)
        assertTrue(result.numbers.none { it in generated })
        assertFalse(result.partial)
    }

    @Test(timeout = 10_000)
    fun `no repeats - repeated picks on large range stay unique and in range`() {
        val generator = generator(seed = 99L)
        val min = 1
        val max = 500_000
        val generated = mutableListOf<Int>()

        repeat(300) {
            val result = generator.next(min = min, max = max, allowRepeats = false, generated = generated)
            result as GenerationResult.Success
            assertTrue("выдано исключённое число ${result.number}", result.number !in generated)
            assertTrue("число ${result.number} вне диапазона", result.number in min..max)
            generated += result.number
        }
        assertEquals(300, generated.toSet().size)
    }

    @Test
    fun `no repeats - values outside the range do not block generation`() {
        val generator = generator()
        // «Чужое» число вне диапазона не должно считаться занятой ячейкой.
        val generated = listOf(-100, 0, 999)

        val result = generator.next(min = 1, max = 10, allowRepeats = false, generated = generated)

        result as GenerationResult.Success
        assertTrue(result.number in 1..10)
    }
}
