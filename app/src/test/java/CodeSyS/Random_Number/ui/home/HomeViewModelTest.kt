package CodeSyS.Random_Number.ui.home

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionTransfer
import CodeSyS.Random_Number.platform.HonestDraw
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
import org.junit.Assert.assertFalse
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

    @Test
    fun `duplicateSession - creates a copy with a new id and empty history`() = runTest(dispatcher) {
        repository.create(session("a", lastUsedAt = 100L).withGenerated(listOf(3), at = 100L))
        val vm = HomeViewModel(repository, clock = { 500L })
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.duplicateSession("a")
        advanceUntilIdle()

        val copies = repository.observeSessions().first().filter { it.id != "a" }
        assertEquals(1, copies.size)
        assertTrue(copies.single().log.isEmpty())
        assertEquals(500L, copies.single().createdAt)
        assertTrue(events.any { it is HomeEvent.SessionDuplicated })
    }

    @Test
    fun `duplicateSession - missing session changes nothing`() = runTest(dispatcher) {
        val vm = HomeViewModel(repository)

        vm.duplicateSession("missing")
        advanceUntilIdle()

        assertTrue(repository.observeSessions().first().isEmpty())
    }

    // --- Поиск / фильтр / сортировка / избранное (3.5–3.7) ---

    @Test
    fun `filter - archived sessions are hidden by default and shown in archive`() =
        runTest(dispatcher) {
            repository.create(session("visible", lastUsedAt = 100L))
            repository.create(session("hidden", lastUsedAt = 200L).withArchived(true))
            val vm = HomeViewModel(repository)

            val all = vm.uiState.first { !it.isLoading }
            assertEquals(listOf("visible"), all.sessions.map { it.id })

            vm.setFilter(SessionFilter.ARCHIVE)
            val archive = vm.uiState.first { it.filter == SessionFilter.ARCHIVE && !it.isLoading }
            assertEquals(listOf("hidden"), archive.sessions.map { it.id })
        }

    @Test
    fun `filter - favorites shows only favorite non-archived sessions`() = runTest(dispatcher) {
        repository.create(session("plain", lastUsedAt = 100L))
        repository.create(session("fav", lastUsedAt = 200L).withFavorite(true))
        val vm = HomeViewModel(repository)

        vm.setFilter(SessionFilter.FAVORITES)
        val state = vm.uiState.first { it.filter == SessionFilter.FAVORITES && !it.isLoading }

        assertEquals(listOf("fav"), state.sessions.map { it.id })
    }

    @Test
    fun `search - matches tags and localized title`() = runTest(dispatcher) {
        repository.create(session("a", lastUsedAt = 100L).withTags(listOf("Лотерея")))
        repository.create(session("b", lastUsedAt = 200L))
        val vm = HomeViewModel(repository)

        vm.setQuery("лотерея")
        val byTag = vm.uiState.first { it.query == "лотерея" && !it.isLoading }
        assertEquals(listOf("a"), byTag.sessions.map { it.id })

        vm.setQuery("1..10")
        val byTitle = vm.uiState.first { it.query == "1..10" && !it.isLoading }
        assertEquals(listOf("b", "a"), byTitle.sessions.map { it.id })
    }

    @Test
    fun `sort - by title uses localized titles`() = runTest(dispatcher) {
        repository.create(
            Session(id = "z", title = "t", min = 5, max = 9, allowRepeats = true, lastUsedAt = 100L),
        )
        repository.create(
            Session(id = "a", title = "t", min = 1, max = 3, allowRepeats = true, lastUsedAt = 200L),
        )
        val vm = HomeViewModel(repository)

        vm.setSort(SessionSort.TITLE)
        val state = vm.uiState.first { it.sort == SessionSort.TITLE && !it.isLoading }

        assertEquals(listOf("a", "z"), state.sessions.map { it.id })
    }

    @Test
    fun `sort - by progress puts most picked first`() = runTest(dispatcher) {
        repository.create(session("low", lastUsedAt = 300L))
        repository.create(session("high", lastUsedAt = 100L).withGenerated(listOf(1, 2, 3), at = 100L))
        val vm = HomeViewModel(repository)

        vm.setSort(SessionSort.PROGRESS)
        val state = vm.uiState.first { it.sort == SessionSort.PROGRESS && !it.isLoading }

        assertEquals(listOf("high", "low"), state.sessions.map { it.id })
    }

    @Test
    fun `toggleFavorite and toggleArchived - persist flags`() = runTest(dispatcher) {
        repository.create(session("a", lastUsedAt = 100L))
        val vm = HomeViewModel(repository)

        vm.toggleFavorite("a")
        advanceUntilIdle()
        assertTrue(repository.getSession("a")!!.isFavorite)

        vm.toggleArchived("a")
        advanceUntilIdle()
        assertTrue(repository.getSession("a")!!.isArchived)
    }

    @Test
    fun `setTags - normalizes and persists`() = runTest(dispatcher) {
        repository.create(session("a", lastUsedAt = 100L))
        val vm = HomeViewModel(repository)

        vm.setTags("a", listOf(" x ", "", "x", "y"))
        advanceUntilIdle()

        assertEquals(listOf("x", "y"), repository.getSession("a")!!.tags)
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
        assertFalse(event.share)
    }

    @Test
    fun `exportAllSessions share - flags the event for the share sheet`() = runTest(dispatcher) {
        repository.create(session("a", lastUsedAt = 100L))
        val vm = HomeViewModel(repository)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.exportAllSessions(exportedAt = 1L, share = true)
        advanceUntilIdle()

        assertTrue(events.filterIsInstance<HomeEvent.ExportReady>().single().share)
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

    // --- Честный розыгрыш ---

    private fun honestViewModel(seed: Long = 7L) = HomeViewModel(
        repository,
        HonestDraw(repository = repository, seedSource = { seed }, clock = { 1L }),
    )

    @Test
    fun `drawHonestNumber - creates a secure-seeded session with one result`() = runTest(dispatcher) {
        val vm = honestViewModel(seed = 7L)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.drawHonestNumber()
        advanceUntilIdle()

        val drawn = events.filterIsInstance<HomeEvent.HonestDrawReady>().single().session
        assertEquals(7L, drawn.seed)
        assertTrue(drawn.seedFromSecure)
        assertEquals(1, drawn.generated.size)
        assertEquals(drawn, repository.getSession(drawn.id))
    }

    @Test
    fun `drawHonestFromList - emits a list draw with one of the participants`() = runTest(dispatcher) {
        val vm = honestViewModel(seed = 5L)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.drawHonestFromList(listOf("a", "b", "c"))
        advanceUntilIdle()

        val drawn = events.filterIsInstance<HomeEvent.HonestDrawReady>().single().session
        assertTrue(drawn.isItemsMode)
        assertTrue(drawn.lastDisplay in listOf("a", "b", "c"))
    }

    @Test
    fun `drawHonestFromList - empty list reports failure and draws nothing`() = runTest(dispatcher) {
        val vm = honestViewModel(seed = 5L)
        val events = mutableListOf<HomeEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.drawHonestFromList(emptyList())
        advanceUntilIdle()

        assertTrue(events.any { it is HomeEvent.HonestDrawFailed })
        assertTrue(repository.observeSessions().first().isEmpty())
    }
}
