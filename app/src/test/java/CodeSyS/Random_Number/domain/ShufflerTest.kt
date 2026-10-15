package CodeSyS.Random_Number.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShufflerTest {

    private val items = listOf("Аня", "Борис", "Вера", "Гриша", "Даша")

    @Test
    fun `shuffle - keeps every element exactly once`() {
        val result = Shuffler(Random(42)).shuffle(items)

        assertEquals(items.size, result.size)
        assertEquals(items.toSet(), result.toSet())
    }

    @Test
    fun `shuffle - does not mutate the source list`() {
        val source = items.toList()

        Shuffler(Random(42)).shuffle(source)

        assertEquals(items, source)
    }

    @Test
    fun `shuffle - same seed reproduces the same order`() {
        val a = Shuffler(Random(7)).shuffle(items)
        val b = Shuffler(Random(7)).shuffle(items)

        assertEquals(a, b)
    }

    @Test
    fun `shuffle - different seeds usually give different orders`() {
        val a = Shuffler(Random(1)).shuffle(items)
        val b = Shuffler(Random(2)).shuffle(items)

        assertNotEquals(a, b)
    }

    @Test
    fun `shuffle - eventually produces every permutation position`() {
        // За много прогонов элементы оказываются на разных позициях —
        // тасование не оставляет список в исходном порядке.
        val firstElements = (1..50).map { seed ->
            Shuffler(Random(seed.toLong())).shuffle(items).first()
        }.toSet()

        assertTrue(firstElements.size > 1)
    }

    @Test
    fun `shuffle - single element list is returned as is`() {
        assertEquals(listOf("only"), Shuffler(Random(1)).shuffle(listOf("only")))
    }

    @Test
    fun `shuffle - numbers are shuffled too`() {
        val result = Shuffler(Random(3)).shuffle((1..10).toList())

        assertEquals((1..10).toSet(), result.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `shuffle - empty list throws`() {
        Shuffler(Random(1)).shuffle(emptyList<Int>())
    }
}
