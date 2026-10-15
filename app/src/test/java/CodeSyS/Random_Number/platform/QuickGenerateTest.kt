package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.GeneratedEntry
import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.domain.SessionGenerator
import CodeSyS.Random_Number.domain.createSessionGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickGenerateTest {

    private var now = 1_000L

    private fun numbers(
        id: String = "s1",
        min: Int = 1,
        max: Int = 10,
        allowRepeats: Boolean = true,
        generated: List<Int> = emptyList(),
    ) = Session(
        id = id,
        title = "$min..$max",
        min = min,
        max = max,
        allowRepeats = allowRepeats,
        log = generated.map { GeneratedEntry(it, 0L) },
        lastUsedAt = now,
    )

    private fun quickGenerate(repository: InMemorySessionRepository) = QuickGenerate(
        repository = repository,
        generator = SessionGenerator(),
        clock = { now },
    )

    private suspend fun stored(repository: InMemorySessionRepository) =
        repository.observeSessions().first()

    @Test
    fun `generateLatest - generates into the most recent session`() = runTest {
        val repository = InMemorySessionRepository()
        now = 500L
        repository.create(numbers(id = "old"))
        now = 900L
        repository.create(numbers(id = "recent"))
        now = 2_000L

        val updated = quickGenerate(repository).generateLatest()

        assertEquals("recent", updated?.id)
        assertEquals(1, updated?.generated?.size)
        assertEquals(1, repository.getSession("recent")?.generated?.size)
        assertEquals(0, repository.getSession("old")?.generated?.size)
    }

    @Test
    fun `generateLatest - no sessions returns null`() = runTest {
        assertNull(quickGenerate(InMemorySessionRepository()).generateLatest())
    }

    @Test
    fun `generateIn - generates in the given session`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(numbers(id = "a"))
        repository.create(numbers(id = "b"))

        val updated = quickGenerate(repository).generateIn("b")

        assertEquals("b", updated?.id)
        assertEquals(1, repository.getSession("b")?.generated?.size)
        assertEquals(0, repository.getSession("a")?.generated?.size)
    }

    @Test
    fun `generateIn - missing session returns null`() = runTest {
        assertNull(quickGenerate(InMemorySessionRepository()).generateIn("missing"))
    }

    @Test
    fun `generateLatest - exhausted session returns null and does not change it`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(
            numbers(min = 1, max = 2, allowRepeats = false, generated = listOf(1, 2)),
        )

        val updated = quickGenerate(repository).generateLatest()

        assertNull(updated)
        assertEquals(2, repository.getSession("s1")?.generated?.size)
    }

    @Test
    fun `generateIn - dice appends one entry per die`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(
            Session(
                id = "d",
                title = "2d6",
                payload = SessionPayload.Dice(count = 2, sides = 6),
                lastUsedAt = now,
            ),
        )

        val updated = quickGenerate(repository).generateIn("d")

        assertEquals(2, updated?.generated?.size)
        assertTrue(updated!!.generated.all { it in 1..6 })
    }

    @Test
    fun `generateIn - deleted session is not resurrected`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(numbers(id = "gone"))
        repository.delete("gone")

        assertNull(quickGenerate(repository).generateIn("gone"))
        assertNull(repository.getSession("gone"))
    }

    @Test
    fun `generateIn - seed makes generation reproducible`() = runTest {
        val repository = InMemorySessionRepository()
        repository.create(numbers(id = "a", min = 1, max = 1000).copy(seed = 777L))
        repository.create(numbers(id = "b", min = 1, max = 1000).copy(seed = 777L))

        val qg = QuickGenerate(
            repository = repository,
            clock = { now },
            generatorFactory = { seed -> createSessionGenerator(seed) },
        )
        qg.generateIn("a")
        qg.generateIn("b")

        assertEquals(
            repository.getSession("a")?.generated,
            repository.getSession("b")?.generated,
        )
    }

    @Test
    fun `generateLatest - updates lastUsedAt`() = runTest {
        val repository = InMemorySessionRepository()
        now = 100L
        repository.create(numbers(id = "s1"))
        now = 5_000L

        val updated = quickGenerate(repository).generateLatest()

        assertEquals(5_000L, updated?.lastUsedAt)
    }
}
