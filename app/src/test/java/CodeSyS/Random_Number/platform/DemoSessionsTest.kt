package CodeSyS.Random_Number.platform

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.SessionPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSessionsTest {

    /** Фейк флага «демо созданы»: MutableStateFlow для повторных чтений. */
    private class SeededFlag {
        val flow = MutableStateFlow(false)

        fun markTrue() {
            flow.value = true
        }
    }

    @Test
    fun `seedIfFirstRun - creates demo sessions on empty storage`() = runTest {
        val repository = InMemorySessionRepository()
        val flag = SeededFlag()
        val demo = DemoSessions(
            repository = repository,
            demoSeeded = flag.flow,
            markDemoSeeded = { flag.markTrue() },
        )

        val created = demo.seedIfFirstRun()

        assertEquals(3, created.size)
        assertEquals(3, repository.observeSessionsOnce().size)
        // Проверяем состав: числа, кубики, монета.
        assertTrue(created.any { it.isNumbersMode })
        assertTrue(created.any { it.payload is SessionPayload.Dice })
        assertTrue(created.any { it.isCoinMode })
    }

    @Test
    fun `seedIfFirstRun - skips when flag is already set`() = runTest {
        val repository = InMemorySessionRepository()
        val flag = SeededFlag().also { it.markTrue() }
        val demo = DemoSessions(
            repository = repository,
            demoSeeded = flag.flow,
            markDemoSeeded = { flag.markTrue() },
        )

        val created = demo.seedIfFirstRun()

        assertTrue(created.isEmpty())
        assertTrue(repository.observeSessionsOnce().isEmpty())
    }

    @Test
    fun `seedIfFirstRun - marks the flag so second call does not duplicate`() = runTest {
        val repository = InMemorySessionRepository()
        val flag = SeededFlag()
        val demo = DemoSessions(
            repository = repository,
            demoSeeded = flag.flow,
            markDemoSeeded = { flag.markTrue() },
        )

        demo.seedIfFirstRun()
        val second = demo.seedIfFirstRun()

        assertTrue(second.isEmpty())
        assertEquals(3, repository.observeSessionsOnce().size)
    }

    @Test
    fun `seedIfFirstRun - skips when user already has sessions`() = runTest {
        val repository = InMemorySessionRepository()
        val existingSession = CodeSyS.Random_Number.data.Session.new(
            min = 1,
            max = 10,
            allowRepeats = true,
            now = 0L,
        )
        repository.create(existingSession)
        val flag = SeededFlag()
        val demo = DemoSessions(
            repository = repository,
            demoSeeded = flag.flow,
            markDemoSeeded = { flag.markTrue() },
        )

        val created = demo.seedIfFirstRun()

        assertTrue(created.isEmpty())
        assertEquals(1, repository.observeSessionsOnce().size)
    }
}
