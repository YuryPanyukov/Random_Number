package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.domain.SessionGenerationResult
import CodeSyS.Random_Number.domain.createSessionGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HonestDrawTest {

    private var now = 1_000L

    private fun draw(
        repository: InMemorySessionRepository,
        seed: Long = 42L,
    ) = HonestDraw(
        repository = repository,
        seedSource = { seed },
        clock = { now },
    )

    @Test
    fun `drawNumber - creates a numbered session with a secure seed and one result`() = runTest {
        val repository = InMemorySessionRepository()

        val session = draw(repository).drawNumber()!!

        assertEquals(HonestDraw.TITLE, session.title)
        assertEquals(42L, session.seed)
        assertTrue(session.seedFromSecure)
        assertEquals(1, session.log.size)
        assertTrue(session.generated.single() in HonestDraw.MIN..HonestDraw.MAX)
        assertFalse(session.allowRepeats)
        assertEquals(session, repository.getSession(session.id))
    }

    @Test
    fun `drawNumber - honors custom range`() = runTest {
        val repository = InMemorySessionRepository()

        val session = draw(repository).drawNumber(min = 10, max = 20)!!

        assertEquals(10, session.min)
        assertEquals(20, session.max)
        assertTrue(session.generated.single() in 10..20)
    }

    @Test
    fun `drawNumber - the same seed produces the same result`() = runTest {
        val repository = InMemorySessionRepository()
        val drawer = draw(repository, seed = 777L)

        val first = drawer.drawNumber()!!
        val second = drawer.drawNumber()!!

        assertEquals(first.generated, second.generated)
        assertEquals(2, repository.observeSessions().first().size)
    }

    @Test
    fun `drawItem - picks one of the list items`() = runTest {
        val repository = InMemorySessionRepository()

        val session = draw(repository).drawItem(listOf("Аня", "Борис", "Вика"))!!

        assertEquals(HonestDraw.LIST_TITLE, session.title)
        assertTrue(session.isItemsMode)
        assertTrue(session.seedFromSecure)
        assertEquals(1, session.generated.size)
        assertTrue(session.lastDisplay in listOf("Аня", "Борис", "Вика"))
        assertEquals(session, repository.getSession(session.id))
    }

    @Test
    fun `drawItem - empty list draws nothing`() = runTest {
        val repository = InMemorySessionRepository()

        assertNull(draw(repository).drawItem(emptyList()))
        assertTrue(repository.observeSessions().first().isEmpty())
    }

    @Test
    fun `drawNumber - stored seed reproduces the result independently`() = runTest {
        val repository = InMemorySessionRepository()
        val session = draw(repository, seed = 123L).drawNumber()!!

        // Независимая проверка: свежая сессия с тем же seed даёт тот же результат.
        val replay = Session.new(
            min = session.min,
            max = session.max,
            allowRepeats = false,
            now = now,
            seed = session.seed,
        )
        val result = createSessionGenerator(session.seed!!).generate(replay)

        assertTrue(result is SessionGenerationResult.Success)
        assertEquals(
            session.generated.single(),
            (result as SessionGenerationResult.Success).values.single(),
        )
    }
}
