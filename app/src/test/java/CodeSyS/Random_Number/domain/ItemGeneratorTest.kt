package CodeSyS.Random_Number.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemGeneratorTest {

    private val items = listOf("Аня", "Борис", "Вера", "Гриша", "Даша")

    // --- Базовый выбор ---

    @Test
    fun `next - returns requested count of items from the list`() {
        val generator = ItemGenerator(Random(42))

        val result = generator.next(count = 3, items = items, allowRepeats = false)

        result as ItemGenerationResult.Success
        assertEquals(3, result.items.size)
        assertTrue(result.items.all { it in items })
        assertEquals(3, result.indices.toSet().size)
    }

    @Test
    fun `next - same seed reproduces the same picks`() {
        val a = ItemGenerator(Random(7)).next(3, items, allowRepeats = false)
        val b = ItemGenerator(Random(7)).next(3, items, allowRepeats = false)

        assertEquals(a, b)
    }

    @Test
    fun `next - different seeds pick different orders`() {
        val a = ItemGenerator(Random(1)).next(5, items, allowRepeats = false)
        val b = ItemGenerator(Random(2)).next(5, items, allowRepeats = false)

        assertTrue(a != b)
    }

    @Test
    fun `next - indices point at the returned items`() {
        val generator = ItemGenerator(Random(3))

        val result = generator.next(count = 4, items = items, allowRepeats = false)
            as ItemGenerationResult.Success

        assertEquals(result.items, result.indices.map { items[it] })
    }

    // --- Без повторений ---

    @Test
    fun `next without repeats - no item appears twice and generated are excluded`() {
        val generator = ItemGenerator(Random(99))

        val result = generator.next(
            count = 3,
            items = items,
            allowRepeats = false,
            generated = listOf(0, 1), // «Аня» и «Борис» уже выпали
        ) as ItemGenerationResult.Success

        assertEquals(3, result.items.size)
        assertEquals(3, result.items.toSet().size)
        assertTrue(result.items.none { it == "Аня" || it == "Борис" })
    }

    @Test
    fun `next without repeats - asking more than remains is partial`() {
        val generator = ItemGenerator(Random(5))

        val result = generator.next(
            count = 10,
            items = items,
            allowRepeats = false,
            generated = listOf(0), // осталось 4 из 5
        ) as ItemGenerationResult.Success

        assertEquals(4, result.items.size)
        assertTrue(result.partial)
    }

    @Test
    fun `next without repeats - all picked is Exhausted`() {
        val generator = ItemGenerator(Random(5))

        val result = generator.next(
            count = 1,
            items = items,
            allowRepeats = false,
            generated = items.indices.toList(),
        )

        assertEquals(ItemGenerationResult.Exhausted, result)
    }

    // --- Повторы ---

    @Test
    fun `next with repeats - always returns the requested count`() {
        val generator = ItemGenerator(Random(11))

        repeat(20) {
            val result = generator.next(3, items, allowRepeats = true)
                as ItemGenerationResult.Success
            assertEquals(3, result.items.size)
            assertTrue(result.items.all { it in items })
        }
    }

    @Test
    fun `next with repeats - single element list still picks`() {
        val result = ItemGenerator(Random(1)).next(5, listOf("only"), allowRepeats = true)
            as ItemGenerationResult.Success

        assertEquals(List(5) { "only" }, result.items)
    }

    // --- Валидация ---

    @Test(expected = IllegalArgumentException::class)
    fun `next - empty list throws`() {
        ItemGenerator(Random(1)).next(count = 1, items = emptyList(), allowRepeats = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `next - zero count throws`() {
        ItemGenerator(Random(1)).next(count = 0, items = items, allowRepeats = true)
    }
}
