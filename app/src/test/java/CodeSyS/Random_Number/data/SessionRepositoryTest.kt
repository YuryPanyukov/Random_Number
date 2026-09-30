package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт [SessionRepository]: одинаковые проверки для in-memory
 * реализации. Постоянное хранилище использует ту же раскладку
 * ([SessionStorage]), поэтому правила поведения общие.
 */
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
        log = generated.map { GeneratedEntry(it, 0L) },
        createdAt = lastUsedAt,
        lastUsedAt = lastUsedAt,
    )

    // --- Создание и чтение ---

    @Test
    fun `create - new session appears in observed list`() = runTest {
        val repository = InMemorySessionRepository()
        assertTrue(repository.create(session("a", lastUsedAt = 100L)))

        assertEquals(listOf("a"), repository.observeSessions().first().map { it.id })
    }

    @Test
    fun `create - existing id is not overwritten`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L, generated = listOf(1)))

        assertFalse(repository.create(session("a", lastUsedAt = 200L, generated = listOf(9))))

        val stored = repository.getSession("a")!!
        assertEquals(listOf(1), stored.generated)
        assertEquals(100L, stored.lastUsedAt)
    }

    @Test
    fun `getSession - returns created session`() = runTest {
        val repository = InMemorySessionRepository()
        val original = session("a", lastUsedAt = 100L, generated = listOf(3, 7))
        repository.create(original)

        assertEquals(original, repository.getSession("a"))
    }

    @Test
    fun `getSession - unknown id returns null`() = runTest {
        val repository = InMemorySessionRepository()
        assertNull(repository.getSession("missing"))
    }

    // --- Обновление (P0.4: удалённая сессия не воскресает) ---

    @Test
    fun `update - changes existing session`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L, generated = listOf(1)))

        val updated = repository.getSession("a")!!.withGenerated(listOf(2), at = 200L)
        assertTrue(repository.update(updated))

        assertEquals(listOf(1, 2), repository.getSession("a")!!.generated)
        assertEquals(1, repository.observeSessions().first().size)
    }

    @Test
    fun `update - deleted session is not resurrected`() = runTest {
        val repository = InMemorySessionRepository()
        val original = session("a", lastUsedAt = 100L, generated = listOf(1))
        repository.create(original)
        repository.delete("a")

        assertFalse(repository.update(original.withGenerated(listOf(2), at = 200L)))

        assertNull(repository.getSession("a"))
        assertTrue(repository.observeSessions().first().isEmpty())
    }

    // --- Сортировка ---

    @Test
    fun `observeSessions - sorted by lastUsedAt descending`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("old", lastUsedAt = 100L))
        repository.create(session("new", lastUsedAt = 300L))
        repository.create(session("mid", lastUsedAt = 200L))

        assertEquals(
            listOf("new", "mid", "old"),
            repository.observeSessions().first().map { it.id },
        )
    }

    @Test
    fun `clearNumbers - moved session stays first in list`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L, generated = listOf(1, 2)))
        repository.create(session("b", lastUsedAt = 200L))

        // Сброс меняет lastUsedAt на 999 — сессия «a» должна стать первой.
        val cleared = repository.clearNumbers("a", at = 999L)

        assertEquals(999L, cleared?.lastUsedAt)
        assertEquals(listOf("a", "b"), repository.observeSessions().first().map { it.id })
    }

    // --- Удаление ---

    @Test
    fun `delete - removes session`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))

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
        repository.create(session("a", lastUsedAt = 100L, generated = listOf(1, 2, 3)))

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

    // --- Повреждённые данные (P0.1) ---

    @Test
    fun `observeSessions - corrupted entry is skipped but not deleted`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))
        repository.putRaw("session_a", "{битый json")

        val sessions = repository.observeSessions().first()

        assertEquals(listOf("b"), sessions.map { it.id })
        // Запись на месте — её можно починить/экспортировать вручную.
        assertTrue("session_a" in repository.rawKeys())
        assertEquals(1, repository.corrupted.size)
        assertEquals("a", repository.corrupted.single().first)
    }

    @Test
    fun `update of another session - does not touch corrupted entry`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))
        repository.putRaw("session_a", "{битый json")

        repository.update(repository.getSession("b")!!.withGenerated(listOf(5), at = 500L))

        // «a» не превратилась в «b» и не исчезла из ключей.
        assertTrue("session_a" in repository.rawKeys())
        assertEquals(listOf(5), repository.getSession("b")!!.generated)
    }

    @Test
    fun `observeSessions - broken index is rebuilt from keys`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))
        repository.putRaw(SessionStorage.INDEX_KEY, "[[[")

        assertEquals(
            listOf("b", "a"),
            repository.observeSessions().first().map { it.id },
        )
    }

    @Test
    fun `observeSessions - entry missing from index is still visible`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))
        // Индекс «забыл» про b.
        repository.putRaw(SessionStorage.INDEX_KEY, SessionCodec.encodeIds(listOf("a")))

        assertEquals(
            listOf("b", "a"),
            repository.observeSessions().first().map { it.id },
        )
    }

    // --- Миграция со старого формата ---

    @Test
    fun `legacy format - sessions are readable and migrated on write`() = runTest {
        val legacy = SessionCodec.encode(
            listOf(
                session("a", lastUsedAt = 100L, generated = listOf(1)),
                session("b", lastUsedAt = 300L),
            ),
        )
        val repository = InMemorySessionRepository()
        repository.putRaw(SessionStorage.LEGACY_KEY, legacy)

        // Читаются сразу, даже до записи.
        assertEquals(listOf("b", "a"), repository.observeSessions().first().map { it.id })
        assertEquals(listOf(1), repository.getSession("a")!!.generated)

        // Запись переносит данные в новый формат.
        repository.update(repository.getSession("a")!!.withGenerated(listOf(2), at = 500L))
        assertEquals(listOf(1, 2), repository.getSession("a")!!.generated)
        assertTrue("session_a" in repository.rawKeys())
        assertTrue(SessionStorage.LEGACY_KEY !in repository.rawKeys())
    }

    @Test
    fun `legacy format - corrupted legacy data is not silently dropped`() = runTest {
        val repository = InMemorySessionRepository()
        repository.putRaw(SessionStorage.LEGACY_KEY, "{не json")

        assertTrue(repository.observeSessions().first().isEmpty())
        // Исходная строка осталась на месте — её можно восстановить вручную.
        assertTrue(SessionStorage.LEGACY_KEY in repository.rawKeys())
    }

    @Test
    fun `delete - removes session from legacy data`() = runTest {
        val legacy = SessionCodec.encode(
            listOf(session("a", lastUsedAt = 100L), session("b", lastUsedAt = 200L)),
        )
        val repository = InMemorySessionRepository()
        repository.putRaw(SessionStorage.LEGACY_KEY, legacy)

        repository.delete("a")

        assertEquals(listOf("b"), repository.observeSessions().first().map { it.id })
    }

    // --- Контракт модели ---

    @Test
    fun `session - isExhausted only without repeats`() {
        val noRepeats = session("a", lastUsedAt = 1L, min = 1, max = 3, generated = listOf(1, 2, 3))
        assertTrue(noRepeats.isExhausted)

        val withRepeats = session(
            "a",
            lastUsedAt = 1L,
            min = 1,
            max = 3,
            allowRepeats = true,
            generated = listOf(1, 2, 3),
        )
        assertTrue(!withRepeats.isExhausted)
    }

    @Test
    fun `session - partial generation is not exhausted`() {
        val partial = session("a", lastUsedAt = 1L, min = 1, max = 10, generated = listOf(1, 2))
        assertTrue(!partial.isExhausted)
        assertEquals(2, partial.pickedCount)
    }
}
