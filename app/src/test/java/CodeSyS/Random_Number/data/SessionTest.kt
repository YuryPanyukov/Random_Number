package CodeSyS.Random_Number.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private fun session(
        min: Int = 1,
        max: Int = 10,
        allowRepeats: Boolean = false,
        generated: List<Int> = emptyList(),
        at: Long = 0L,
        seed: Long? = null,
    ) = Session(
        id = "s1",
        title = "$min..$max",
        min = min,
        max = max,
        allowRepeats = allowRepeats,
        log = generated.map { GeneratedEntry(it, at) },
        createdAt = 0L,
        lastUsedAt = 0L,
        seed = seed,
    )

    // --- Батч-генерация (1.1) ---

    @Test
    fun `withGenerated list - appends batch and updates lastUsedAt`() {
        val updated = session().withGenerated(listOf(3, 7, 1), at = 999L)

        assertEquals(listOf(3, 7, 1), updated.generated)
        assertEquals(999L, updated.lastUsedAt)
    }

    @Test
    fun `withGenerated list - appends after existing history`() {
        val updated = session(generated = listOf(5)).withGenerated(listOf(6), at = 100L)
        assertEquals(listOf(5, 6), updated.generated)
    }

    // --- Отмена последнего числа (1.2) ---

    @Test
    fun `withoutLastGenerated - drops last number and updates lastUsedAt`() {
        val updated = session(generated = listOf(3, 7)).withoutLastGenerated(at = 500L)

        assertEquals(listOf(3), updated.generated)
        assertEquals(500L, updated.lastUsedAt)
    }

    @Test
    fun `withoutLastGenerated - empty history stays empty`() {
        val updated = session().withoutLastGenerated(at = 500L)

        assertTrue(updated.generated.isEmpty())
        assertEquals(500L, updated.lastUsedAt)
    }

    // --- Ручное редактирование истории (1.6) ---

    @Test
    fun `removeGeneratedAt - removes number by index`() {
        val updated = session(generated = listOf(5, 9, 2)).removeGeneratedAt(1, at = 700L)

        assertEquals(listOf(5, 2), updated.generated)
        assertEquals(700L, updated.lastUsedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `removeGeneratedAt - index out of bounds throws`() {
        session(generated = listOf(1, 2)).removeGeneratedAt(2, at = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `removeGeneratedAt - negative index throws`() {
        session(generated = listOf(1, 2)).removeGeneratedAt(-1, at = 1L)
    }

    @Test
    fun `addManual - valid number appended`() {
        val updated = session(generated = listOf(5)).addManual(7, at = 800L)

        assertEquals(listOf(5, 7), updated.generated)
        assertEquals(800L, updated.lastUsedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addManual - number below range throws`() {
        session(min = 1, max = 10).addManual(0, at = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addManual - number above range throws`() {
        session(min = 1, max = 10).addManual(11, at = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `addManual - duplicate without repeats throws`() {
        session(generated = listOf(5)).addManual(5, at = 1L)
    }

    @Test
    fun `addManual - duplicate allowed with repeats`() {
        val updated = session(allowRepeats = true, generated = listOf(5)).addManual(5, at = 1L)
        assertEquals(listOf(5, 5), updated.generated)
    }

    // --- Валидация диапазона в withGenerated (P2.1) ---

    @Test(expected = IllegalArgumentException::class)
    fun `withGenerated number - out of range throws`() {
        session(min = 1, max = 10).withGenerated(11, at = 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withGenerated list - any value out of range throws`() {
        session(min = 1, max = 10).withGenerated(listOf(3, 42), at = 1L)
    }

    @Test
    fun `withGenerated list - values on range bounds are allowed`() {
        val updated = session(min = 1, max = 10).withGenerated(listOf(1, 10), at = 1L)
        assertEquals(listOf(1, 10), updated.generated)
    }

    @Test
    fun `withGenerated list - negative ranges are supported`() {
        val updated = session(min = -10, max = -1).withGenerated(listOf(-10, -1, -5), at = 1L)
        assertEquals(listOf(-10, -1, -5), updated.generated)
    }

    @Test
    fun `withGenerated list - empty batch only updates lastUsedAt`() {
        val updated = session(generated = listOf(1)).withGenerated(emptyList(), at = 500L)
        assertEquals(listOf(1), updated.generated)
        assertEquals(500L, updated.lastUsedAt)
    }

    // --- Вычисляемые свойства ---

    @Test
    fun `pickedCount - counts distinct values`() {
        assertEquals(0, session().pickedCount)
        assertEquals(2, session(allowRepeats = true, generated = listOf(5, 5, 7)).pickedCount)
    }

    @Test
    fun `rangeSize - covers boundaries and full int range`() {
        assertEquals(1, session(min = 7, max = 7).rangeSize)
        assertEquals(10, session(min = 1, max = 10).rangeSize)
        assertEquals(
            Int.MAX_VALUE.toLong(),
            session(min = Int.MIN_VALUE, max = Int.MAX_VALUE).rangeSize.toLong(),
        )
    }

    @Test
    fun `isExhausted - false while free values remain`() {
        assertFalse(session(min = 1, max = 3, generated = listOf(1, 2)).isExhausted)
    }

    // --- Журнал с таймстампами ---

    @Test
    fun `withGenerated - every number gets the batch timestamp`() {
        val updated = session().withGenerated(listOf(3, 7, 1), at = 1_234L)

        assertEquals(listOf(1_234L, 1_234L, 1_234L), updated.log.map { it.at })
        assertEquals(listOf(3, 7, 1), updated.generated)
        assertEquals(1, updated.lastNumber)
    }

    @Test
    fun `withoutLastGenerated - removes the entry with its timestamp`() {
        val updated = session(generated = listOf(3), at = 10L)
            .withGenerated(listOf(7), at = 20L)
            .withoutLastGenerated(at = 30L)

        assertEquals(listOf(3), updated.generated)
        assertEquals(10L, updated.log.single().at)
        assertEquals(3, updated.lastNumber)
    }

    @Test
    fun `removeGeneratedAt - keeps timestamps of remaining entries`() {
        val updated = session(generated = listOf(5), at = 10L)
            .withGenerated(listOf(9), at = 20L)
            .withGenerated(listOf(2), at = 30L)
            .removeGeneratedAt(1, at = 40L)

        assertEquals(listOf(10L, 30L), updated.log.map { it.at })
    }

    @Test
    fun `lastNumber - null for empty history`() {
        assertNull(session().lastNumber)
    }

    @Test
    fun `generatedCount - counts entries including repeats`() {
        val session = session(allowRepeats = true, generated = listOf(1, 1, 2))

        assertEquals(3, session.generatedCount)
        assertEquals(2, session.pickedCount)
    }

    // --- Seed ---

    @Test
    fun `withSeed - sets and clears the seed`() {
        val session = session()

        assertEquals(42L, session.withSeed(42L).seed)
        assertNull(session.withSeed(42L).withSeed(null).seed)
    }

    // --- Дублирование (3.3) ---

    @Test
    fun `duplicate - new id, same settings, empty history and fresh times`() {
        val original = session(allowRepeats = false, generated = listOf(1, 2))
            .copy(id = "orig", seed = 7L, seedFromSecure = true)

        val copy = original.duplicate(now = 999L)

        assertNotEquals("orig", copy.id)
        assertEquals(original.payload, copy.payload)
        assertEquals(7L, copy.seed)
        assertTrue(copy.seedFromSecure)
        assertTrue(copy.log.isEmpty())
        assertEquals(999L, copy.createdAt)
        assertEquals(999L, copy.lastUsedAt)
        // Оригинал не изменяется.
        assertEquals(listOf(1, 2), original.generated)
    }

    @Test
    fun `duplicate - explicit id is used`() {
        assertEquals("copy", session().duplicate(now = 1L, id = "copy").id)
    }

    // --- Теги/избранное/архив (3.5–3.6) ---

    @Test
    fun `withFavorite and withArchived - set flags`() {
        assertTrue(session().withFavorite(true).isFavorite)
        assertTrue(session().withArchived(true).isArchived)
    }

    @Test
    fun `withTags - trims, drops blanks and duplicates`() {
        val tagged = session().withTags(listOf("  лотерея ", "", "игры", "лотерея"))

        assertEquals(listOf("лотерея", "игры"), tagged.tags)
    }

    @Test
    fun `duplicate - copies tags but resets favorite and archive`() {
        val original = session()
            .withTags(listOf("a"))
            .withFavorite(true)
            .withArchived(true)

        val copy = original.duplicate(now = 1L)

        assertEquals(listOf("a"), copy.tags)
        assertFalse(copy.isFavorite)
        assertFalse(copy.isArchived)
    }

    @Test
    fun `new - seed defaults to null and can be set`() {
        assertNull(Session.new(min = 1, max = 10, allowRepeats = true, now = 1L).seed)
        assertEquals(7L, Session.new(min = 1, max = 10, allowRepeats = true, now = 1L, seed = 7L).seed)
    }

    @Test
    fun `new - generated history is empty and times are set`() {
        val session = Session.new(min = 1, max = 10, allowRepeats = false, now = 555L)

        assertTrue(session.log.isEmpty())
        assertEquals(555L, session.createdAt)
        assertEquals(555L, session.lastUsedAt)
    }

    // --- Заметки (3.4) ---

    @Test
    fun `withDescription - sets note text`() {
        val session = session()
        val withNote = session.withDescription("Нужно для совещания")

        assertEquals("Нужно для совещания", withNote.description)
        assertEquals("", session.description) // original unchanged
    }

    @Test
    fun `duplicate - copies description`() {
        val original = session().withDescription("Моя заметка")
        val copy = original.duplicate(now = 1L)

        assertEquals("Моя заметка", copy.description)
    }

    // --- Редактирование сессии (3.2) ---

    @Test
    fun `withTitle - changes session title`() {
        val session = session()
        val renamed = session.withTitle("Новый заголовок")

        assertEquals("Новый заголовок", renamed.title)
        assertEquals(session.title, session.title) // original unchanged
    }

    @Test
    fun `withNumbersRange - updates range for numbers mode`() {
        val session = session(min = 1, max = 10)
        val updated = session.withNumbersRange(min = 1, max = 50, allowRepeats = true)

        assertEquals(50, updated.max)
        assertTrue(updated.allowRepeats)
    }

    @Test
    fun `withNumbersRange - throws for non-numbers mode`() {
        val itemsSession = Session.new(
            payload = SessionPayload.Items(listOf("a", "b"), allowRepeats = true),
            now = 1L,
        )
        try {
            itemsSession.withNumbersRange(min = 1, max = 10, allowRepeats = true)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("withNumbersRange доступен только для режима чисел", e.message)
        }
    }

    @Test
    fun `withItems - updates items for items mode`() {
        val itemsSession = Session.new(
            payload = SessionPayload.Items(listOf("a", "b"), allowRepeats = true),
            now = 1L,
        )
        val updated = itemsSession.withItems(listOf("x", "y", "z"), allowRepeats = false)

        assertEquals(listOf("x", "y", "z"), (updated.payload as SessionPayload.Items).items)
        assertFalse(updated.allowRepeats)
    }

    @Test
    fun `withItems - updates items for shuffle mode`() {
        val shuffleSession = Session.new(
            payload = SessionPayload.Shuffle(listOf("a", "b")),
            now = 1L,
        )
        val updated = shuffleSession.withItems(listOf("x", "y", "z"), allowRepeats = true)

        assertEquals(listOf("x", "y", "z"), (updated.payload as SessionPayload.Shuffle).items)
    }

    @Test
    fun `withItems - throws for non-list mode`() {
        val session = session()
        try {
            session.withItems(listOf("x"), allowRepeats = true)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("withItems доступен только для режимов списка/перемешивания", e.message)
        }
    }

    @Test
    fun `withTitle and withDescription - chain correctly`() {
        val session = session()
        val updated = session.withTitle("Новый").withDescription("Заметка")

        assertEquals("Новый", updated.title)
        assertEquals("Заметка", updated.description)
    }

    private fun fail(message: String): Nothing {
        throw AssertionError(message)
    }
}
