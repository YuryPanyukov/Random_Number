package CodeSyS.Random_Number.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecTest {

    private val sample = Session(
        id = "id-1",
        title = "1..10, без повторов",
        min = 1,
        max = 10,
        allowRepeats = false,
        generated = listOf(3, 7, 1),
        createdAt = 1_000L,
        lastUsedAt = 2_000L,
    )

    @Test
    fun `encode then decode - restores session as-is`() {
        val decoded = SessionCodec.decode(SessionCodec.encode(listOf(sample)))
        assertEquals(listOf(sample), decoded)
    }

    @Test
    fun `encode then decode - keeps order of sessions`() {
        val other = sample.copy(id = "id-2", lastUsedAt = 3_000L)
        val decoded = SessionCodec.decode(SessionCodec.encode(listOf(sample, other)))
        assertEquals(listOf("id-1", "id-2"), decoded.map { it.id })
    }

    @Test
    fun `decode - empty input returns empty list`() {
        assertEquals(emptyList<Session>(), SessionCodec.decode(null))
        assertEquals(emptyList<Session>(), SessionCodec.decode(""))
        assertEquals(emptyList<Session>(), SessionCodec.decode("   "))
    }

    @Test
    fun `decode - corrupted json returns empty list instead of crash`() {
        assertEquals(emptyList<Session>(), SessionCodec.decode("{не json!"))
        assertEquals(emptyList<Session>(), SessionCodec.decode("""{"unexpected":1}"""))
    }

    @Test
    fun `decode - unknown fields are ignored`() {
        val json = """
            [{"id":"x","title":"t","min":1,"max":5,"allowRepeats":true,
              "generated":[],"createdAt":0,"lastUsedAt":0,"someNewField":42}]
        """.trimIndent()
        val decoded = SessionCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals("x", decoded.first().id)
    }

    @Test
    fun `encode - computed properties are not serialized`() {
        val encoded = SessionCodec.encode(listOf(sample))
        assertTrue(!encoded.contains("rangeSize"))
        assertTrue(!encoded.contains("isExhausted"))
        assertTrue(!encoded.contains("pickedCount"))
    }
}
