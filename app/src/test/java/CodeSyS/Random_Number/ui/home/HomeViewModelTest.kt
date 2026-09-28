package CodeSyS.Random_Number.ui.home

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemorySessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemorySessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun session(id: String, lastUsedAt: Long) = Session(
        id = id,
        title = "1..10",
        min = 1,
        max = 10,
        allowRepeats = false,
        generated = emptyList(),
        createdAt = lastUsedAt,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun `uiState - shows sessions from repository sorted by lastUsedAt desc`() = runTest(dispatcher) {
        repository.save(session("old", lastUsedAt = 100L))
        repository.save(session("new", lastUsedAt = 300L))

        val vm = HomeViewModel(repository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(listOf("new", "old"), state.sessions.map { it.id })
    }

    @Test
    fun `uiState - empty repository yields empty list`() = runTest(dispatcher) {
        val vm = HomeViewModel(repository)
        val state = vm.uiState.first { !it.isLoading }

        assertTrue(state.sessions.isEmpty())
    }

    @Test
    fun `deleteSession - removes session from state`() = runTest(dispatcher) {
        repository.save(session("a", lastUsedAt = 100L))
        repository.save(session("b", lastUsedAt = 200L))

        val vm = HomeViewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.deleteSession("a")
        advanceUntilIdle()

        val state = vm.uiState.first { it.sessions.map { s -> s.id } == listOf("b") }
        assertEquals(listOf("b"), state.sessions.map { it.id })
    }
}
