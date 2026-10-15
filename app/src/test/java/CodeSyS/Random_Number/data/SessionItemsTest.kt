package CodeSyS.Random_Number.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionItemsTest {

    private val items = listOf("Аня", "Борис", "Вера")

    private fun session(
        allowRepeats: Boolean = false,
        log: List<GeneratedEntry> = emptyList(),
    ) = Session(
        id = "i1",
        title = "Список (3)",
        payload = SessionPayload.Items(items = items, allowRepeats = allowRepeats),
        log = log,
        createdAt = 100L,
        lastUsedAt = 100L,
    )

    // --- Параметры режима ---

    @Test
    fun `items mode - exposes list and index bounds`() {
        val session = session()

        assertTrue(session.isItemsMode)
        assertEquals(items, session.items)
        assertEquals(0, session.min)
        assertEquals(2, session.max)
        assertEquals(3, session.rangeSize)
        assertFalse(session.allowRepeats)
    }

    @Test
    fun `numbers mode - is not items mode and has empty item list`() {
        val numbers = Session.new(min = 1, max = 10, allowRepeats = false, now = 0L)

        assertFalse(numbers.isItemsMode)
        assertTrue(numbers.items.isEmpty())
    }

    // --- Отображение значений ---

    @Test
    fun `displayOf - maps log index to item, numbers to text`() {
        val session = session(log = listOf(GeneratedEntry(2, 0L), GeneratedEntry(0, 0L)))

        assertEquals("Вера", session.displayOf(2))
        assertEquals(listOf("Вера", "Аня"), session.generatedDisplay)
        assertEquals("Аня", session.lastDisplay)
    }

    @Test
    fun `displayOf - unknown index falls back to placeholder`() {
        assertEquals("?", session().displayOf(99))
    }

    // --- Прогресс и исчерпание ---

    @Test
    fun `pickedCount - counts distinct picked items`() {
        val session = session(
            allowRepeats = true,
            log = listOf(GeneratedEntry(0, 0L), GeneratedEntry(0, 0L), GeneratedEntry(1, 0L)),
        )

        assertEquals(2, session.pickedCount)
        assertEquals(3, session.generatedCount)
    }

    @Test
    fun `isExhausted - true when all items picked without repeats`() {
        val exhausted = session(
            log = listOf(GeneratedEntry(0, 0L), GeneratedEntry(1, 0L), GeneratedEntry(2, 0L)),
        )

        assertTrue(exhausted.isExhausted)
    }

    @Test
    fun `isExhausted - false when repeats are allowed`() {
        val session = session(
            allowRepeats = true,
            log = listOf(GeneratedEntry(0, 0L), GeneratedEntry(1, 0L), GeneratedEntry(2, 0L)),
        )

        assertFalse(session.isExhausted)
    }

    // --- Валидация индексов ---

    @Test
    fun `withGenerated - indices applied to log`() {
        val updated = session().withGenerated(listOf(2, 0), at = 500L)

        assertEquals(listOf(2, 0), updated.generated)
        assertEquals(listOf("Вера", "Аня"), updated.generatedDisplay)
        assertEquals(500L, updated.lastUsedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withGenerated - index beyond the list throws`() {
        session().withGenerated(listOf(3), at = 1L)
    }

    // --- Кодек ---

    @Test
    fun `encode - items payload is written with type discriminator`() {
        val encoded = SessionCodec.encodeOne(session())

        val payload = Json.parseToJsonElement(encoded).jsonObject.getValue("payload").jsonObject
        assertEquals("items", payload.getValue("type").jsonPrimitive.content)
        assertEquals(
            listOf("Аня", "Борис", "Вера"),
            payload.getValue("items").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `round trip - items session survives encode and decode`() {
        val original = session(log = listOf(GeneratedEntry(1, 7L)))

        assertEquals(original, SessionCodec.decodeOne(SessionCodec.encodeOne(original)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `payload - empty items list is rejected`() {
        SessionPayload.Items(items = emptyList(), allowRepeats = true)
    }
}
