package CodeSyS.Random_Number.data

import CodeSyS.Random_Number.domain.CoinSide
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoinTest {

    private fun session(log: List<GeneratedEntry> = emptyList()) = Session(
        id = "c1",
        title = "Монета",
        payload = SessionPayload.Coin,
        log = log,
        createdAt = 100L,
        lastUsedAt = 100L,
    )

    // --- Параметры режима ---

    @Test
    fun `coin mode - has two outcomes and always repeats`() {
        val session = session()

        assertTrue(session.isCoinMode)
        assertFalse(session.isNumbersMode)
        assertEquals(0, session.min)
        assertEquals(1, session.max)
        assertEquals(2, session.rangeSize)
        assertTrue(session.allowRepeats)
        assertFalse(session.isExhausted)
    }

    // --- Отображение ---

    @Test
    fun `displayOf - maps log values to heads and tails`() {
        val session = session(
            log = listOf(GeneratedEntry(0, 0L), GeneratedEntry(1, 0L), GeneratedEntry(0, 0L)),
        )

        assertEquals("Орёл", session.displayOf(0))
        assertEquals("Решка", session.displayOf(1))
        assertEquals(listOf("Орёл", "Решка", "Орёл"), session.generatedDisplay)
        assertEquals("Орёл", session.lastDisplay)
    }

    @Test
    fun `counts - heads and tails are counted`() {
        val session = session(
            log = listOf(GeneratedEntry(0, 0L), GeneratedEntry(0, 0L), GeneratedEntry(1, 0L)),
        )

        assertEquals(2, session.coinHeadsCount)
        assertEquals(1, session.coinTailsCount)
    }

    @Test
    fun `counts - empty history is all zeros`() {
        assertEquals(0, session().coinHeadsCount)
        assertEquals(0, session().coinTailsCount)
    }

    // --- Валидация ---

    @Test(expected = IllegalArgumentException::class)
    fun `withGenerated - value above one throws`() {
        session().withGenerated(listOf(2), at = 1L)
    }

    @Test
    fun `withGenerated - both sides accepted`() {
        val updated = session().withGenerated(listOf(0, 1, 1), at = 5L)

        assertEquals(listOf(0, 1, 1), updated.generated)
        assertEquals(1, updated.coinHeadsCount)
        assertEquals(2, updated.coinTailsCount)
    }

    // --- Кодек ---

    @Test
    fun `encode - coin payload is written with type discriminator`() {
        val encoded = SessionCodec.encodeOne(session())

        val payload = Json.parseToJsonElement(encoded).jsonObject.getValue("payload").jsonObject
        assertEquals("coin", payload.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `round trip - coin session survives encode and decode`() {
        val original = session(log = listOf(GeneratedEntry(1, 7L)))

        assertEquals(original, SessionCodec.decodeOne(SessionCodec.encodeOne(original)))
    }

    @Test
    fun `coin side - log values match the enum ordinals`() {
        assertEquals(CoinSide.HEADS.ordinal, 0)
        assertEquals(CoinSide.TAILS.ordinal, 1)
    }
}
