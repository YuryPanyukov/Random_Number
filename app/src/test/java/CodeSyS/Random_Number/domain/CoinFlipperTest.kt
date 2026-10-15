package CodeSyS.Random_Number.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinFlipperTest {

    @Test
    fun `flip - over many tosses both sides appear`() {
        val flipper = CoinFlipper(Random(42))

        val sides = (1..200).map { flipper.flip() }.toSet()

        assertEquals(setOf(CoinSide.HEADS, CoinSide.TAILS), sides)
    }

    @Test
    fun `flip - same seed reproduces the same sequence`() {
        val a = CoinFlipper(Random(7)).let { flipper -> (1..20).map { flipper.flip() } }
        val b = CoinFlipper(Random(7)).let { flipper -> (1..20).map { flipper.flip() } }

        assertEquals(a, b)
    }

    @Test
    fun `flip count - returns requested number of sides`() {
        val flips = CoinFlipper(Random(1)).flip(5)

        assertEquals(5, flips.size)
        assertTrue(flips.all { it == CoinSide.HEADS || it == CoinSide.TAILS })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `flip - zero count throws`() {
        CoinFlipper(Random(1)).flip(0)
    }

    // --- CoinSide ---

    @Test
    fun `of - maps log values to sides`() {
        assertEquals(CoinSide.HEADS, CoinSide.of(0))
        assertEquals(CoinSide.TAILS, CoinSide.of(1))
        // Некорректное значение не роняет вывод.
        assertEquals(CoinSide.HEADS, CoinSide.of(99))
    }

    @Test
    fun `ordinal - heads is 0 and tails is 1`() {
        assertEquals(0, CoinSide.HEADS.ordinal)
        assertEquals(1, CoinSide.TAILS.ordinal)
    }
}
