package CodeSyS.Random_Number.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecTest {

    private val sample = Session(
        id = "id-1",
        title = "1..10, без повторов",
        min = 1,
        max = 10,
        allowRepeats = false,
        log = listOf(
            GeneratedEntry(3, 1_000L),
            GeneratedEntry(7, 1_500L),
            GeneratedEntry(1, 2_000L),
        ),
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

    // --- Журнал с таймстампами и обратная совместимость ---

    @Test
    fun `encode then decode - timestamps survive`() {
        val decoded = SessionCodec.decodeOne(SessionCodec.encodeOne(sample))

        assertEquals(1_000L, decoded!!.log[0].at)
        assertEquals(1_500L, decoded.log[1].at)
        assertEquals(listOf(3, 7, 1), decoded.generated)
    }

    @Test
    fun `decode - legacy flat generated list is migrated to log`() {
        val legacy = """
            {"id":"x","title":"t","min":1,"max":5,"allowRepeats":true,
             "generated":[4,2,4],"createdAt":0,"lastUsedAt":0}
        """.trimIndent()

        val decoded = SessionCodec.decodeOne(legacy)!!

        assertEquals(listOf(4, 2, 4), decoded.generated)
        // Время в старых данных неизвестно — проставляется 0.
        assertTrue(decoded.log.all { it.at == 0L })
    }

    @Test
    fun `decode - legacy list of sessions is migrated`() {
        val legacy = """
            [{"id":"a","title":"t","min":1,"max":5,"allowRepeats":true,
              "generated":[1,2],"createdAt":0,"lastUsedAt":0}]
        """.trimIndent()

        assertEquals(listOf(1, 2), SessionCodec.decode(legacy).single().generated)
    }

    @Test
    fun `decode - legacy and current formats coexist`() {
        val mixed = """
            [{"id":"a","title":"t","min":1,"max":5,"allowRepeats":true,
              "generated":[1],"createdAt":0,"lastUsedAt":0},
             {"id":"b","title":"t","min":1,"max":5,"allowRepeats":true,
              "log":[{"value":9,"at":42}],"createdAt":0,"lastUsedAt":0}]
        """.trimIndent()

        val decoded = SessionCodec.decode(mixed)

        assertEquals(listOf(1), decoded[0].generated)
        assertEquals(42L, decoded[1].log.single().at)
    }

    @Test
    fun `decode - seed is preserved`() {
        val session = sample.withSeed(1234L)

        assertEquals(1234L, SessionCodec.decodeOne(SessionCodec.encodeOne(session))!!.seed)
    }

    @Test
    fun `decode - missing seed defaults to null`() {
        val withoutSeed = """
            {"id":"x","title":"t","min":1,"max":5,"allowRepeats":true,
             "log":[],"createdAt":0,"lastUsedAt":0}
        """.trimIndent()

        assertNull(SessionCodec.decodeOne(withoutSeed)!!.seed)
    }

    @Test
    fun `decodeResult - distinguishes empty and corrupted`() {
        assertEquals(SessionCodec.DecodeResult.Empty, SessionCodec.decodeResult(null))
        assertEquals(SessionCodec.DecodeResult.Empty, SessionCodec.decodeResult("  "))
        assertTrue(SessionCodec.decodeResult("{битый") is SessionCodec.DecodeResult.Corrupted)
        assertTrue(SessionCodec.decodeResult("[]") is SessionCodec.DecodeResult.Success)
    }

    @Test
    fun `decodeResult - corrupted keeps raw payload for diagnostics`() {
        val raw = "{битый"

        val result = SessionCodec.decodeResult(raw) as SessionCodec.DecodeResult.Corrupted

        assertEquals(raw, result.raw)
        assertTrue(result.cause is Exception)
    }

    @Test
    fun `decodeOne - broken payload returns null`() {
        assertNull(SessionCodec.decodeOne("{битый"))
        assertNull(SessionCodec.decodeOne(""))
    }

    // --- Индекс идентификаторов ---

    @Test
    fun `ids - encode then decode`() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, SessionCodec.decodeIds(SessionCodec.encodeIds(ids)))
    }

    @Test
    fun `ids - missing or broken index returns null`() {
        assertNull(SessionCodec.decodeIds(null))
        assertNull(SessionCodec.decodeIds(""))
        assertNull(SessionCodec.decodeIds("{битый"))
    }
}
