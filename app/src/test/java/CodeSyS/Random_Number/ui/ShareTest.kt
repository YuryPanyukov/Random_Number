package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.data.FakeSessionTexts
import CodeSyS.Random_Number.data.GeneratedEntry
import CodeSyS.Random_Number.data.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTest {

    private fun session(
        title: String = "1..100",
        values: List<Int> = emptyList(),
        seed: Long? = null,
        seedFromSecure: Boolean = false,
    ) = Session(
        id = "s",
        title = title,
        min = 1,
        max = 100,
        allowRepeats = true,
        log = values.map { GeneratedEntry(it, 0L) },
        seed = seed,
        seedFromSecure = seedFromSecure,
    )

    @Test
    fun `buildShareText - title only when history is empty`() {
        assertEquals("1..100", buildShareText(session()))
    }

    @Test
    fun `buildShareText - title and values without seed`() {
        val text = buildShareText(session(values = listOf(4, 8)))

        assertEquals("1..100: 4, 8", text)
        assertFalse(text.contains("Seed"))
    }

    @Test
    fun `buildShareText - secure seed adds seed, source and verification hint`() {
        // seedFromSecure = true ⇒ сессия опознаётся как честный розыгрыш.
        val text = buildShareText(session(values = listOf(7), seed = 42L, seedFromSecure = true))

        assertTrue(text.startsWith("Честный розыгрыш: 7"))
        assertTrue(text.contains("Seed: 42 (SecureRandom)"))
        assertTrue(text.contains("Проверка"))
    }

    @Test
    fun `buildShareText - manual seed is labelled as manual`() {
        val text = buildShareText(session(values = listOf(7), seed = 42L))

        assertTrue(text.contains("Seed: 42 (задан вручную)"))
    }

    @Test
    fun `buildShareText - title and service strings come from provided texts`() {
        val text = buildShareText(session(values = listOf(7), seed = 42L), FakeSessionTexts())

        assertTrue(text.startsWith("TITLE: 7"))
        assertTrue(text.contains("Seed: 42 (MANUAL)"))
        assertTrue(text.contains("VERIFY"))
    }
}
