package CodeSyS.Random_Number.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDiceTest {

    private fun session(
        count: Int = 2,
        sides: Int = 6,
        log: List<GeneratedEntry> = emptyList(),
    ) = Session(
        id = "d1",
        title = "${count}d$sides",
        payload = SessionPayload.Dice(count = count, sides = sides),
        log = log,
        createdAt = 100L,
        lastUsedAt = 100L,
    )

    // --- Параметры режима ---

    @Test
    fun `dice mode - exposes bounds drawn from the die`() {
        val session = session(count = 3, sides = 20)

        assertTrue(session.isDiceMode)
        assertFalse(session.isNumbersMode)
        assertEquals(1, session.min)
        assertEquals(20, session.max)
        assertEquals(20, session.rangeSize)
        // Кубики бросаются повторно.
        assertTrue(session.allowRepeats)
        assertFalse(session.isExhausted)
    }

    @Test
    fun `payload label - formats dice notation`() {
        assertEquals("2d6", SessionPayload.Dice(count = 2, sides = 6).label)
        assertEquals("5d20", SessionPayload.Dice(count = 5, sides = 20).label)
    }

    // --- Сумма последнего броска ---

    @Test
    fun `lastDiceRoll - takes the trailing count entries`() {
        val session = session(
            count = 2,
            log = listOf(
                GeneratedEntry(3, 0L),
                GeneratedEntry(5, 0L),
                GeneratedEntry(6, 0L),
                GeneratedEntry(1, 0L),
            ),
        )

        assertEquals(listOf(6, 1), session.lastDiceRoll)
        assertEquals("7", session.lastDisplay)
    }

    @Test
    fun `lastDiceRoll - null until a full roll is present`() {
        val session = session(count = 3, log = listOf(GeneratedEntry(4, 0L)))

        assertNull(session.lastDiceRoll)
        assertNull(session.lastDisplay)
    }

    @Test
    fun `displayOf - die values shown as numbers`() {
        val session = session(log = listOf(GeneratedEntry(4, 0L), GeneratedEntry(2, 0L)))

        assertEquals("4", session.displayOf(4))
        assertEquals(listOf("4", "2"), session.generatedDisplay)
    }

    // --- Валидация ---

    @Test(expected = IllegalArgumentException::class)
    fun `payload - zero dice rejected`() {
        SessionPayload.Dice(count = 0, sides = 6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `payload - single faced die rejected`() {
        SessionPayload.Dice(count = 1, sides = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `withGenerated - die value above sides throws`() {
        session(sides = 6).withGenerated(listOf(7), at = 1L)
    }

    // --- Кодек ---

    @Test
    fun `encode - dice payload is written with type discriminator`() {
        val encoded = SessionCodec.encodeOne(session(count = 2, sides = 6))

        val payload = Json.parseToJsonElement(encoded).jsonObject.getValue("payload").jsonObject
        assertEquals("dice", payload.getValue("type").jsonPrimitive.content)
        assertEquals(2, payload.getValue("count").jsonPrimitive.int)
        assertEquals(6, payload.getValue("sides").jsonPrimitive.int)
    }

    @Test
    fun `round trip - dice session survives encode and decode`() {
        val original = session(count = 3, sides = 8, log = listOf(GeneratedEntry(8, 7L)))

        assertEquals(original, SessionCodec.decodeOne(SessionCodec.encodeOne(original)))
    }
}
