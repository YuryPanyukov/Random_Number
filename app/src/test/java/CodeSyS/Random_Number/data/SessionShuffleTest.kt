package CodeSyS.Random_Number.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionShuffleTest {

    private val items = listOf("Аня", "Борис", "Вера")

    private fun session(log: List<GeneratedEntry> = emptyList()) = Session(
        id = "sh1",
        title = "Перемешивание (3)",
        payload = SessionPayload.Shuffle(items = items),
        log = log,
        createdAt = 100L,
        lastUsedAt = 100L,
    )

    // --- Параметры режима ---

    @Test
    fun `shuffle mode - exposes list and index bounds, always repeats`() {
        val session = session()

        assertTrue(session.isShuffleMode)
        assertFalse(session.isNumbersMode)
        assertFalse(session.isItemsMode)
        assertEquals(items, session.items)
        assertEquals(0, session.min)
        assertEquals(2, session.max)
        assertEquals(3, session.rangeSize)
        assertTrue(session.allowRepeats)
        assertFalse(session.isExhausted)
    }

    @Test
    fun `numbers mode - is not shuffle mode`() {
        val numbers = Session.new(min = 1, max = 10, allowRepeats = false, now = 0L)

        assertFalse(numbers.isShuffleMode)
    }

    // --- Отображение ---

    @Test
    fun `displayOf - maps log indices to items`() {
        val session = session(log = listOf(GeneratedEntry(2, 0L), GeneratedEntry(0, 0L)))

        assertEquals("Вера", session.displayOf(2))
        assertEquals(listOf("Вера", "Аня"), session.generatedDisplay)
    }

    @Test
    fun `lastShuffle - takes the trailing permutation and joins it`() {
        val session = session(
            log = listOf(
                GeneratedEntry(0, 0L),
                GeneratedEntry(1, 0L),
                GeneratedEntry(2, 0L),
                GeneratedEntry(2, 0L),
                GeneratedEntry(0, 0L),
                GeneratedEntry(1, 0L),
            ),
        )

        assertEquals(listOf("Вера", "Аня", "Борис"), session.lastShuffle)
        assertEquals("Вера → Аня → Борис", session.lastDisplay)
    }

    @Test
    fun `lastShuffle - null until a full permutation is present`() {
        val session = session(log = listOf(GeneratedEntry(0, 0L), GeneratedEntry(1, 0L)))

        assertNull(session.lastShuffle)
        assertNull(session.lastDisplay)
    }

    // --- Валидация ---

    @Test(expected = IllegalArgumentException::class)
    fun `payload - empty list is rejected`() {
        SessionPayload.Shuffle(items = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withGenerated - index beyond the list throws`() {
        session().withGenerated(listOf(3), at = 1L)
    }

    // --- Кодек ---

    @Test
    fun `encode - shuffle payload is written with type discriminator`() {
        val encoded = SessionCodec.encodeOne(session())

        val payload = Json.parseToJsonElement(encoded).jsonObject.getValue("payload").jsonObject
        assertEquals("shuffle", payload.getValue("type").jsonPrimitive.content)
        assertEquals(
            listOf("Аня", "Борис", "Вера"),
            payload.getValue("items").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `round trip - shuffle session survives encode and decode`() {
        val original = session(log = listOf(GeneratedEntry(1, 7L)))

        assertEquals(original, SessionCodec.decodeOne(SessionCodec.encodeOne(original)))
    }
}
