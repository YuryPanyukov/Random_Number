package CodeSyS.Random_Number.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollerTest {

    // --- Бросок ---

    @Test
    fun `roll - returns one value per die, all in range`() {
        val roller = DiceRoller(Random(42))

        repeat(50) {
            val roll = roller.roll(count = 5, sides = 6)

            assertEquals(5, roll.dice.size)
            assertTrue(roll.dice.all { it in 1..6 })
        }
    }

    @Test
    fun `roll - covers all faces of the die over time`() {
        val roller = DiceRoller(Random(7))

        val faces = (1..500).flatMap { roller.roll(1, 6).dice }.toSet()

        assertEquals((1..6).toSet(), faces)
    }

    @Test
    fun `roll - same seed reproduces the same roll`() {
        val a = DiceRoller(Random(777)).roll(count = 3, sides = 20)
        val b = DiceRoller(Random(777)).roll(count = 3, sides = 20)

        assertEquals(a, b)
    }

    @Test
    fun `roll - different seeds produce different rolls`() {
        val a = DiceRoller(Random(1)).roll(count = 5, sides = 6)
        val b = DiceRoller(Random(2)).roll(count = 5, sides = 6)

        assertTrue(a != b)
    }

    // --- DiceRoll ---

    @Test
    fun `sum - adds all dice`() {
        assertEquals(12, DiceRoll(listOf(4, 2, 6), sides = 6).sum)
        assertEquals(1, DiceRoll(listOf(1), sides = 6).sum)
    }

    @Test
    fun `label - shows count and sides`() {
        assertEquals("2d6", DiceRoll(listOf(3, 5), sides = 6).label)
    }

    @Test
    fun `formatDetail - joins values with plus`() {
        assertEquals("4 + 2 + 6", DiceRoll.formatDetail(listOf(4, 2, 6)))
    }

    // --- Валидация ---

    @Test(expected = IllegalArgumentException::class)
    fun `roll - zero dice throws`() {
        DiceRoller(Random(1)).roll(count = 0, sides = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `roll - negative dice throws`() {
        DiceRoller(Random(1)).roll(count = -1, sides = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `roll - single faced die throws`() {
        DiceRoller(Random(1)).roll(count = 1, sides = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `roll - empty dice list in result throws`() {
        DiceRoll(dice = emptyList(), sides = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `roll - value outside 1-to-sides`() {
        DiceRoll(dice = listOf(7), sides = 6)
    }
}
