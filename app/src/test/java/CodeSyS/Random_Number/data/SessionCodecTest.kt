package CodeSyS.Random_Number.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `decode - seedFromSecure is preserved`() {
        val session = sample.copy(seed = 5L, seedFromSecure = true)

        val decoded = SessionCodec.decodeOne(SessionCodec.encodeOne(session))!!

        assertEquals(5L, decoded.seed)
        assertTrue(decoded.seedFromSecure)
    }

    @Test
    fun `decode - missing seedFromSecure defaults to false`() {
        val withoutSource = """
            {"id":"x","title":"t","min":1,"max":5,"allowRepeats":true,
             "log":[],"createdAt":0,"lastUsedAt":0,"seed":7}
        """.trimIndent()

        assertFalse(SessionCodec.decodeOne(withoutSource)!!.seedFromSecure)
    }

    @Test
    fun `decode - tags, favorite and archive are preserved`() {
        val session = sample.withTags(listOf("a", "b")).withFavorite(true).withArchived(true)

        val decoded = SessionCodec.decodeOne(SessionCodec.encodeOne(session))!!

        assertEquals(listOf("a", "b"), decoded.tags)
        assertTrue(decoded.isFavorite)
        assertTrue(decoded.isArchived)
    }

    @Test
    fun `decode - missing tags and flags default to empty and false`() {
        val legacy = """
            {"id":"x","title":"t","min":1,"max":5,"allowRepeats":true,
             "log":[],"createdAt":0,"lastUsedAt":0}
        """.trimIndent()

        val decoded = SessionCodec.decodeOne(legacy)!!

        assertTrue(decoded.tags.isEmpty())
        assertFalse(decoded.isFavorite)
        assertFalse(decoded.isArchived)
    }

    // --- Payload: дискриминатор type (2.0) ---

    @Test
    fun `encode - payload has type discriminator and range fields`() {
        val encoded = SessionCodec.encodeOne(sample)
        val root = Json.parseToJsonElement(encoded).jsonObject
        val payload = root.getValue("payload").jsonObject

        assertEquals("numbers", payload.getValue("type").jsonPrimitive.content)
        assertEquals(1, payload.getValue("min").jsonPrimitive.int)
        assertEquals(10, payload.getValue("max").jsonPrimitive.int)
        assertEquals(false, payload.getValue("allowRepeats").jsonPrimitive.boolean)
        // Корень больше не содержит поля диапазона — они переехали в payload.
        assertTrue("min" !in root)
        assertTrue("max" !in root)
        assertTrue("allowRepeats" !in root)
    }

    @Test
    fun `decode - current payload format is readable`() {
        val current = """
            {"id":"z","title":"numbers",
             "payload":{"type":"numbers","min":-5,"max":5,"allowRepeats":true},
             "log":[{"value":1,"at":9}],"createdAt":0,"lastUsedAt":9,"seed":null}
        """.trimIndent()

        val decoded = SessionCodec.decodeOne(current)!!

        assertEquals(-5, decoded.min)
        assertEquals(5, decoded.max)
        assertTrue(decoded.allowRepeats)
        assertEquals(listOf(1), decoded.generated)
    }

    @Test
    fun `decode - legacy flat fields are wrapped into payload`() {
        val legacy = """
            {"id":"x","title":"t","min":1,"max":5,"allowRepeats":true,
             "log":[],"createdAt":0,"lastUsedAt":0}
        """.trimIndent()

        val decoded = SessionCodec.decodeOne(legacy)!!

        assertEquals(SessionPayload.Numbers(min = 1, max = 5, allowRepeats = true), decoded.payload)
        assertEquals(1, decoded.min)
    }

    @Test
    fun `decode - legacy flat and legacy log formats combine`() {
        val mixed = """
            [{"id":"a","title":"t","min":1,"max":5,"allowRepeats":true,
              "generated":[1,2],"createdAt":0,"lastUsedAt":0},
             {"id":"b","title":"t","min":2,"max":8,"allowRepeats":false,
              "log":[{"value":7,"at":42}],"createdAt":0,"lastUsedAt":42}]
        """.trimIndent()

        val decoded = SessionCodec.decode(mixed)

        assertEquals(listOf(1, 2), decoded[0].generated)
        assertEquals(1, decoded[0].min)
        assertEquals(2, decoded[1].min)
        assertEquals(8, decoded[1].max)
        assertEquals(42L, decoded[1].log.single().at)
    }

    @Test
    fun `decode - payload without range fields falls back to minimal numbers`() {
        // Обрезанная запись: без корневых min/max декодер не должен падать.
        val broken = """
            {"id":"x","title":"t","log":[],"createdAt":0,"lastUsedAt":0}
        """.trimIndent()

        val decoded = SessionCodec.decodeOne(broken)!!

        assertEquals(SessionPayload.Numbers(min = 1, max = 1, allowRepeats = true), decoded.payload)
    }

    @Test
    fun `round trip - payload survives encode and decode`() {
        val session = Session(
            id = "p1",
            title = "-5..5",
            payload = SessionPayload.Numbers(min = -5, max = 5, allowRepeats = true),
            log = listOf(GeneratedEntry(3, 7L)),
            createdAt = 1L,
            lastUsedAt = 7L,
            seed = 9L,
        )

        assertEquals(session, SessionCodec.decodeOne(SessionCodec.encodeOne(session)))
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
