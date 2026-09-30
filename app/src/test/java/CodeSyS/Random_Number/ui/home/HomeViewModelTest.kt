package CodeSyS.Random_Number.ui.home

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        log = emptyList(),
        createdAt = lastUsedAt,
        lastUsedAt = lastUsedAt,
    )

    @Test
    fun `uiState - shows sessions from repository sorted by lastUsedAt desc`() = runTest(dispatcher) {
        repository.create(session("old", lastUsedAt = 100L))
        repository.create(session("new", lastUsedAt = 300L))

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
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))

        val vm = HomeViewModel(repository)
        vm.uiState.first { !it.isLoading }

        vm.deleteSession("a")
        advanceUntilIdle()

        val state = vm.uiState.first { it.sessions.map { s -> s.id } == listOf("b") }
        assertEquals(listOf("b"), state.sessions.map { it.id })
    }

    // --- Импорт/экспорт ---

    @Test
    fun `exportAllSessions - emits ExportReady with all sessions`() = runTest(dispatcher) {
        repository.create(session("a", lastUsedAt = 100L))
        repository.create(session("b", lastUsedAt = 200L))
        val vm = HomeViewModel(repository)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.exportAllSessions(exportedAt = 500L)
        advanceUntilIdle()

        val event = events.filterIsInstance<HomeEvent.ExportReady>().single()
        assertEquals(2, SessionTransfer.read(event.text)!!.size)
        assertEquals("random-number-sessions.json", event.fileName)
    }

    @Test
    fun `importSessions - creates new sessions with fresh ids`() = runTest(dispatcher) {
        val original = session("a", lastUsedAt = 100L)
        repository.create(original)
        val raw = SessionTransfer.export(listOf(original, session("b", lastUsedAt = 200L)), exportedAt = 1L)
        val vm = HomeViewModel(repository)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        val imported = vm.importSessions(raw)
        advanceUntilIdle()

        assertEquals(2, imported)
        val ids = repository.observeSessions().first().map { it.id }
        assertEquals(3, ids.size) // исходная «a» + две импортированные
        // Новые id не конфликтуют с существующей сессией.
        assertEquals(3, ids.toSet().size)
        assertEquals(2, (events.filterIsInstance<HomeEvent.Imported>().single()).count)
    }

    @Test
    fun `importSessions - broken file reports failure and imports nothing`() = runTest(dispatcher) {
        val vm = HomeViewModel(repository)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        val imported = vm.importSessions("{не json")
        advanceUntilIdle()

        assertEquals(0, imported)
        assertTrue(events.any { it is HomeEvent.ImportFailed })
        assertTrue(repository.observeSessions().first().isEmpty())
    }

    @Test
    fun `importSessions - unsupported version is not imported`() = runTest(dispatcher) {
        val raw = """{"version":999,"exportedAt":0,"sessions":[{"id":"x","title":"t",""" +
            """"min":1,"max":5,"allowRepeats":true,"log":[],"createdAt":0,"lastUsedAt":0}]}"""
        val vm = HomeViewModel(repository)

        assertEquals(0, vm.importSessions(raw))
        assertTrue(repository.observeSessions().first().isEmpty())
    }
}
