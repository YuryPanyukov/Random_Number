package CodeSyS.Random_Number.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomSourceTest {

    @Test
    fun `same seed - produces identical sequence`() {
        val a = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = 42L))
        val b = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = 42L))

        val seqA = List(20) { a.next(1, 1000, allowRepeats = true) }
        val seqB = List(20) { b.next(1, 1000, allowRepeats = true) }

        assertEquals(seqA, seqB)
    }

    @Test
    fun `different seeds - produce different sequences`() {
        val a = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = 1L))
        val b = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = 2L))

        val seqA = List(20) { a.next(1, 1000, allowRepeats = true) }
        val seqB = List(20) { b.next(1, 1000, allowRepeats = true) }

        assertNotEquals(seqA, seqB)
    }

    @Test
    fun `null seed - generator is not reproducible`() {
        val a = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = null))
        val b = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = null))

        val seqA = List(20) { a.next(1, 1_000_000, allowRepeats = true) }
        val seqB = List(20) { b.next(1, 1_000_000, allowRepeats = true) }

        // Совпадение возможно, но крайне маловероятно: это не детерминизм.
        assertNotEquals(seqA, seqB)
    }

    @Test
    fun `seed wins over random mode`() {
        val a = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = 7L))
        val b = NumberGenerator(createRandom(RandomSourceMode.SECURE, seed = 7L))

        val seqA = List(10) { a.next(1, 100, allowRepeats = true) }
        val seqB = List(10) { b.next(1, 100, allowRepeats = true) }

        assertEquals(seqA, seqB)
    }

    @Test
    fun `secure mode - values stay in range`() {
        val generator = NumberGenerator(createRandom(RandomSourceMode.SECURE, seed = null))

        repeat(500) {
            val result = generator.next(min = -50, max = 50, allowRepeats = true)
            result as GenerationResult.Success
            assertTrue("число ${result.number} вне диапазона", result.number in -50..50)
        }
    }

    @Test
    fun `secure mode - nextLong and nextDouble work`() {
        val random = createRandom(RandomSourceMode.SECURE, seed = null)

        repeat(200) {
            val long = random.nextLong(10L, 20L)
            assertTrue(long in 10L until 20L)
            val double = random.nextDouble()
            assertTrue(double >= 0.0 && double < 1.0)
        }
    }

    @Test
    fun `secure mode - wide ranges stay in bounds`() {
        val random = createRandom(RandomSourceMode.SECURE, seed = null)

        repeat(500) {
            // Диапазон шире Int.MAX_VALUE — задействует ветку nextBoundedLong
            // с отбором по 64 битам.
            val long = random.nextLong(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2)
            assertTrue("long=$long вне диапазона", long >= Long.MIN_VALUE / 2)
            assertTrue("long=$long вне диапазона", long < Long.MAX_VALUE / 2)
        }
    }

    @Test
    fun `secure mode - covers whole Int range staying in bounds`() {
        val random = createRandom(RandomSourceMode.SECURE, seed = null)

        repeat(2_000) {
            val value = random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE)
            assertTrue("Выход за диапазон: $value", value in Int.MIN_VALUE until Int.MAX_VALUE)
        }
    }

    @Test
    fun `secure mode - reaches both ends of a narrow range`() {
        val random = createRandom(RandomSourceMode.SECURE, seed = null)

        // Смещение 0 обязано выпадать: это нижняя граница диапазона.
        val values = buildSet { repeat(200) { add(random.nextInt(0, 4)) } }

        assertEquals(setOf(0, 1, 2, 3), values)
    }

    @Test
    fun `secure mode - nextInt bounds are respected at edges`() {
        val random = createRandom(RandomSourceMode.SECURE, seed = null)

        repeat(500) {
            assertEquals(7, random.nextInt(7, 8))
            val value = random.nextInt(Int.MAX_VALUE - 1, Int.MAX_VALUE)
            assertTrue(value in (Int.MAX_VALUE - 1) until Int.MAX_VALUE)
        }
    }

    @Test
    fun `reproducible session - same seed gives same numbers`() {
        fun draw(seed: Long): List<Int> {
            val generator = NumberGenerator(createRandom(RandomSourceMode.DEFAULT, seed = seed))
            val session = generateAll(generator, min = 1, max = 20, count = 10)
            return session
        }

        assertEquals(draw(123L), draw(123L))
        assertNotEquals(draw(123L), draw(124L))
    }

    private fun generateAll(
        generator: NumberGenerator,
        min: Int,
        max: Int,
        count: Int,
    ): List<Int> {
        val generated = mutableListOf<Int>()
        repeat(count) {
            val result = generator.next(min = min, max = max, allowRepeats = false, generated = generated)
            result as GenerationResult.Success
            generated += result.number
        }
        return generated
    }
}
