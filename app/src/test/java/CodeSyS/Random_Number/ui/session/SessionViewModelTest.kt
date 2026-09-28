package CodeSyS.Random_Number.ui.session

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.domain.NumberGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemorySessionRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemorySessionRepository()
        now = 1_000L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(seed: Long = 42L) = SessionViewModel(
        repository = repository,
        generator = NumberGenerator(Random(seed)),
        clock = { now },
    )

    private fun session(
        id: String = "s1",
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
        createdAt = 0L,
        lastUsedAt = 0L,
    )

    // --- Загрузка ---

    @Test
    fun `loadSession - existing session is shown`() = runTest(dispatcher) {
        val saved = session(generated = listOf(3))
        repository.save(saved)

        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        assertEquals(saved, vm.uiState.value.session)
        assertEquals(3, vm.uiState.value.lastNumber)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadSession - missing session emits SessionNotFound`() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<SessionEvent>()
        // Unconfined — подписываемся синхронно до эмита события.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.loadSession("missing")
        advanceUntilIdle()

        assertEquals(listOf<SessionEvent>(SessionEvent.SessionNotFound), events)
    }

    // --- Генерация ---

    @Test
    fun `generate - adds number to session and saves to repository`() = runTest(dispatcher) {
        repository.save(session())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(1, updated.generated.size)
        assertEquals(updated.generated.last(), vm.uiState.value.lastNumber)
        // Сохранено и в репозитории
        assertEquals(updated, repository.getSession("s1"))
        assertTrue(updated.lastUsedAt >= 1_000L)
    }

    @Test
    fun `generate - with repeats never exhausts`() = runTest(dispatcher) {
        repository.save(session(min = 1, max = 2, allowRepeats = true))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        repeat(50) { vm.generate() }
        advanceUntilIdle()

        assertEquals(50, vm.uiState.value.session!!.generated.size)
        assertFalse(vm.uiState.value.showExhaustedDialog)
    }

    @Test
    fun `generate - without repeats shows dialog when all picked`() = runTest(dispatcher) {
        repository.save(session(min = 1, max = 3, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate(); advanceUntilIdle()
        vm.generate(); advanceUntilIdle()
        assertFalse(vm.uiState.value.showExhaustedDialog)

        vm.generate(); advanceUntilIdle() // последнее число диапазона
        vm.generate(); advanceUntilIdle() // исчерпано → диалог

        assertTrue(vm.uiState.value.showExhaustedDialog)
        assertEquals(3, vm.uiState.value.session!!.generated.size)
    }

    @Test
    fun `generate - full range 0-100 without repeats, no numbers after last`() = runTest(dispatcher) {
        repository.save(session(min = 0, max = 100, allowRepeats = false))
        val vm = viewModel(seed = 99L)
        vm.loadSession("s1")
        advanceUntilIdle()

        repeat(101) {
            vm.generate()
            advanceUntilIdle()
        }

        val session = vm.uiState.value.session!!
        assertEquals(101, session.generated.size)
        assertEquals((0..100).toSet(), session.generated.toSet())
        // После последнего числа диалог показан сразу.
        assertTrue(vm.uiState.value.showExhaustedDialog)

        // Дальнейшие попытки не добавляют чисел.
        repeat(5) {
            vm.generate()
            advanceUntilIdle()
        }
        assertEquals(101, vm.uiState.value.session!!.generated.size)
        assertEquals(101, repository.getSession("s1")!!.generated.size)
    }

    // --- Сброс ---

    @Test
    fun `resetNumbers - clears generated and hides dialog`() = runTest(dispatcher) {
        repository.save(session(min = 1, max = 2, generated = listOf(1, 2)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        vm.generate() // исчерпывает → диалог
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showExhaustedDialog)

        vm.resetNumbers()
        advanceUntilIdle()

        val cleared = vm.uiState.value.session!!
        assertTrue(cleared.generated.isEmpty())
        assertNull(vm.uiState.value.lastNumber)
        assertFalse(vm.uiState.value.showExhaustedDialog)
        // Сброшено и в хранилище
        assertTrue(repository.getSession("s1")!!.generated.isEmpty())
    }

    @Test
    fun `dismissExhaustedDialog - hides dialog without changes`() = runTest(dispatcher) {
        repository.save(session(min = 1, max = 1, generated = listOf(1)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showExhaustedDialog)

        vm.dismissExhaustedDialog()

        assertFalse(vm.uiState.value.showExhaustedDialog)
        assertEquals(listOf(1), vm.uiState.value.session!!.generated)
    }
}
