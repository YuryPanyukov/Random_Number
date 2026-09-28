package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    private fun session(
        id: String,
        lastUsedAt: Long,
        min: Int = 1,
        max: Int = 10,
        allowRepeats: Boolean = false,
        generated: List<Int> = emptyList(),
    ) = Session(
        id = id,
        title = "$min..$max",
        min = min,
        max = max,
        allowRepeats = allowRepeats,
        generated = generated,
        createdAt = lastUsedAt,
        lastUsedAt = lastUsedAt,
    )

    // --- Сохранение и чтение ---

    @Test
    fun `save - new session appears in observed list`() = runTest {
        val repository = InMemorySessionRepository()
        repository.save(session("a", lastUsedAt = 100L))

        val sessions = repository.observeSessions().first()
        assertEquals(listOf("a"), sessions.map { it.id })
    }

    @Test
    fun `getSession - returns saved session`() = runTest {
        val repository = InMemorySessionRepository()
        val original = session("a", lastUsedAt = 100L, generated = listOf(3, 7))
        repository.save(original)

        assertEquals(original, repository.getSession("a"))
    }

    @Test
    fun `getSession - unknown id returns null`() = runTest {
        val repository = InMemorySessionRepository()
        assertNull(repository.getSession("missing"))
    }

    @Test
    fun `save - updates existing session with same id`() = runTest {
        val repository = InMemorySessionRepository()
        repository.save(session("a", lastUsedAt = 100L, generated = listOf(1)))
        repository.save(session("a", lastUsedAt = 200L, generated = listOf(1, 2)))

        val sessions = repository.observeSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(listOf(1, 2), sessions.first().generated)
    }

    // --- Сортировка ---

    @Test
    fun `observeSessions - sorted by lastUsedAt descending`() = runTest {
        val repository = InMemorySessionRepository()
        repository.save(session("old", lastUsedAt = 100L))
        repository.save(session("new", lastUsedAt = 300L))
        repository.save(session("mid", lastUsedAt = 200L))

        val sessions = repository.observeSessions().first()
        assertEquals(listOf("new", "mid", "old"), sessions.map { it.id })
    }

    // --- Удаление ---

    @Test
    fun `delete - removes session`() = runTest {
        val repository = InMemorySessionRepository()
        repository.save(session("a", lastUsedAt = 100L))
        repository.save(session("b", lastUsedAt = 200L))

        repository.delete("a")

        assertEquals(listOf("b"), repository.observeSessions().first().map { it.id })
        assertNull(repository.getSession("a"))
    }

    @Test
    fun `delete - unknown id does nothing`() = runTest {
        val repository = InMemorySessionRepository(initial = listOf(session("a", lastUsedAt = 100L)))
        repository.delete("missing")
        assertEquals(1, repository.observeSessions().first().size)
    }

    // --- Сброс ранее выбранных чисел ---

    @Test
    fun `clearNumbers - clears generated list and updates lastUsedAt`() = runTest {
        val repository = InMemorySessionRepository()
        repository.save(
            session("a", lastUsedAt = 100L, generated = listOf(1, 2, 3)),
        )

        val cleared = repository.clearNumbers("a", at = 999L)

        assertEquals(emptyList<Int>(), cleared?.generated)
        assertEquals(999L, cleared?.lastUsedAt)
        assertEquals(emptyList<Int>(), repository.getSession("a")?.generated)
    }

    @Test
    fun `clearNumbers - unknown id returns null`() = runTest {
        val repository = InMemorySessionRepository()
        assertNull(repository.clearNumbers("missing", at = 1L))
    }

    // --- Контракт модели ---

    @Test
    fun `session - isExhausted only without repeats`() {
        val noRepeats = session("a", lastUsedAt = 1L, min = 1, max = 3, generated = listOf(1, 2, 3))
        assertTrue(noRepeats.isExhausted)

        val withRepeats = session("a", lastUsedAt = 1L, min = 1, max = 3, allowRepeats = true, generated = listOf(1, 2, 3))
        assertTrue(!withRepeats.isExhausted)
    }

    @Test
    fun `session - partial generation is not exhausted`() {
        val partial = session("a", lastUsedAt = 1L, min = 1, max = 10, generated = listOf(1, 2))
        assertTrue(!partial.isExhausted)
        assertEquals(2, partial.pickedCount)
    }
}
