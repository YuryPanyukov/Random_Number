package CodeSyS.Random_Number.ui.session

import CodeSyS.Random_Number.data.GeneratedEntry
import CodeSyS.Random_Number.data.HistoryExporter
import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.domain.CoinFlipper
import CodeSyS.Random_Number.domain.DiceRoller
import CodeSyS.Random_Number.domain.ItemGenerator
import CodeSyS.Random_Number.domain.NumberGenerator
import CodeSyS.Random_Number.domain.Shuffler
import CodeSyS.Random_Number.platform.FeedbackProvider
import kotlin.random.Random
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    private fun viewModel(
        seed: Long = 42L,
        feedback: FeedbackProvider? = null,
    ) = SessionViewModel(
        repository = repository,
        generator = NumberGenerator(Random(seed)),
        itemGenerator = ItemGenerator(Random(seed)),
        diceRoller = DiceRoller(Random(seed)),
        coinFlipper = CoinFlipper(Random(seed)),
        shuffler = Shuffler(Random(seed)),
        clock = { now },
        feedback = feedback,
    )

    private fun session(
        id: String = "s1",
        min: Int = 1,
        max: Int = 10,
        allowRepeats: Boolean = false,
        generated: List<Int> = emptyList(),
        seed: Long? = null,
    ) = Session(
        id = id,
        title = "$min..$max",
        min = min,
        max = max,
        allowRepeats = allowRepeats,
        log = generated.map { GeneratedEntry(it, 0L) },
        createdAt = 0L,
        lastUsedAt = 0L,
        seed = seed,
    )

    private fun diceSession(
        id: String = "s1",
        count: Int = 2,
        sides: Int = 6,
        generated: List<Int> = emptyList(),
        seed: Long? = null,
    ) = Session(
        id = id,
        title = "${count}d$sides",
        payload = SessionPayload.Dice(count = count, sides = sides),
        log = generated.map { GeneratedEntry(it, 0L) },
        createdAt = 0L,
        lastUsedAt = 0L,
        seed = seed,
    )

    private fun coinSession(
        id: String = "s1",
        generated: List<Int> = emptyList(),
        seed: Long? = null,
    ) = Session(
        id = id,
        title = "Монета",
        payload = SessionPayload.Coin,
        log = generated.map { GeneratedEntry(it, 0L) },
        createdAt = 0L,
        lastUsedAt = 0L,
        seed = seed,
    )

    private fun itemsSession(
        id: String = "s1",
        allowRepeats: Boolean = false,
        generated: List<Int> = emptyList(),
        seed: Long? = null,
    ) = Session(
        id = id,
        title = "Список (3)",
        payload = SessionPayload.Items(
            items = listOf("Аня", "Борис", "Вера"),
            allowRepeats = allowRepeats,
        ),
        log = generated.map { GeneratedEntry(it, 0L) },
        createdAt = 0L,
        lastUsedAt = 0L,
        seed = seed,
    )

    private fun shuffleSession(
        id: String = "s1",
        generated: List<Int> = emptyList(),
        seed: Long? = null,
    ) = Session(
        id = id,
        title = "Перемешивание (3)",
        payload = SessionPayload.Shuffle(items = listOf("Аня", "Борис", "Вера")),
        log = generated.map { GeneratedEntry(it, 0L) },
        createdAt = 0L,
        lastUsedAt = 0L,
        seed = seed,
    )

    // --- Р—Р°РіСЂСѓР·РєР° ---

    @Test
    fun `loadSession - existing session is shown`() = runTest(dispatcher) {
        val saved = session(generated = listOf(3))
        repository.create(saved)

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
        // Unconfined вЂ” РїРѕРґРїРёСЃС‹РІР°РµРјСЃСЏ СЃРёРЅС…СЂРѕРЅРЅРѕ РґРѕ СЌРјРёС‚Р° СЃРѕР±С‹С‚РёСЏ.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.loadSession("missing")
        advanceUntilIdle()

        assertEquals(listOf<SessionEvent>(SessionEvent.SessionNotFound), events)
    }

    // --- Р“РµРЅРµСЂР°С†РёСЏ ---

    @Test
    fun `generate - adds number to session and saves to repository`() = runTest(dispatcher) {
        repository.create(session())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(1, updated.generated.size)
        assertEquals(updated.generated.last(), vm.uiState.value.lastNumber)
        // РЎРѕС…СЂР°РЅРµРЅРѕ Рё РІ СЂРµРїРѕР·РёС‚РѕСЂРёРё
        assertEquals(updated, repository.getSession("s1"))
        assertTrue(updated.lastUsedAt >= 1_000L)
    }

    @Test
    fun `generate - with repeats never exhausts`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 2, allowRepeats = true))
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
        repository.create(session(min = 1, max = 3, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showExhaustedDialog)

        vm.generate()
        advanceUntilIdle() // РїРѕСЃР»РµРґРЅРµРµ С‡РёСЃР»Рѕ РґРёР°РїР°Р·РѕРЅР°
        vm.generate()
        advanceUntilIdle() // РёСЃС‡РµСЂРїР°РЅРѕ в†’ РґРёР°Р»РѕРі

        assertTrue(vm.uiState.value.showExhaustedDialog)
        assertEquals(3, vm.uiState.value.session!!.generated.size)
    }

    @Test
    fun `generate - full range 0-100 without repeats, no numbers after last`() = runTest(dispatcher) {
        repository.create(session(min = 0, max = 100, allowRepeats = false))
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
        // РџРѕСЃР»Рµ РїРѕСЃР»РµРґРЅРµРіРѕ С‡РёСЃР»Р° РґРёР°Р»РѕРі РїРѕРєР°Р·Р°РЅ СЃСЂР°Р·Сѓ.
        assertTrue(vm.uiState.value.showExhaustedDialog)

        // Р”Р°Р»СЊРЅРµР№С€РёРµ РїРѕРїС‹С‚РєРё РЅРµ РґРѕР±Р°РІР»СЏСЋС‚ С‡РёСЃРµР».
        repeat(5) {
            vm.generate()
            advanceUntilIdle()
        }
        assertEquals(101, vm.uiState.value.session!!.generated.size)
        assertEquals(101, repository.getSession("s1")!!.generated.size)
    }

    // --- РЎР±СЂРѕСЃ ---

    @Test
    fun `resetNumbers - clears generated and hides dialog`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 2, generated = listOf(1, 2)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        vm.generate() // РёСЃС‡РµСЂРїС‹РІР°РµС‚ в†’ РґРёР°Р»РѕРі
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showExhaustedDialog)

        vm.resetNumbers()
        advanceUntilIdle()

        val cleared = vm.uiState.value.session!!
        assertTrue(cleared.generated.isEmpty())
        assertNull(vm.uiState.value.lastNumber)
        assertFalse(vm.uiState.value.showExhaustedDialog)
        // РЎР±СЂРѕС€РµРЅРѕ Рё РІ С…СЂР°РЅРёР»РёС‰Рµ
        assertTrue(repository.getSession("s1")!!.generated.isEmpty())
    }

    @Test
    fun `dismissExhaustedDialog - hides dialog without changes`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 1, generated = listOf(1)))
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

    // --- Р‘Р°С‚С‡-РіРµРЅРµСЂР°С†РёСЏ (1.1) ---

    @Test
    fun `generate - batch of five appends five unique numbers`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 100, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(5, updated.generated.size)
        assertEquals(5, updated.generated.toSet().size)
        assertTrue(updated.generated.all { it in 1..100 })
        assertEquals(updated.generated.last(), vm.uiState.value.lastNumber)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `generate - batch with partial exhaustion adds only remaining`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 3, allowRepeats = false, generated = listOf(1)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        // Из 5 запрошено — добавлены только 2 и 3 (порядок в батче
        // случайный); батч исчерпал диапазон.
        val updated = vm.uiState.value.session!!
        assertEquals(3, updated.generated.size)
        assertEquals(setOf(1, 2, 3), updated.generated.toSet())
        assertTrue(vm.uiState.value.showExhaustedDialog)
        assertTrue(vm.uiState.value.lastBatchPartial)
        assertEquals(updated.generated, repository.getSession("s1")!!.generated)
    }

    @Test
    fun `generate - full batch is not marked partial`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 100, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.lastBatchPartial)
    }

    @Test
    fun `generate - batch on exhausted range shows dialog without changes`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 1, allowRepeats = false, generated = listOf(1)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(10)
        vm.generate()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showExhaustedDialog)
        assertEquals(listOf(1), vm.uiState.value.session!!.generated)
        assertEquals(listOf(1), repository.getSession("s1")!!.generated)
    }

    @Test
    fun `setBatchSize - coerces to at least one`() = runTest(dispatcher) {
        repository.create(session())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(0)
        assertEquals(1, vm.uiState.value.batchSize)
    }

    @Test
    fun `generate - reports batch size to the feedback provider`() = runTest(dispatcher) {
        repository.create(session())
        val feedback = RecordingFeedback()
        val vm = viewModel(feedback = feedback)
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        assertEquals(listOf(5), feedback.counts)
    }

    @Test
    fun `generate - reports single number for batch of one`() = runTest(dispatcher) {
        repository.create(session())
        val feedback = RecordingFeedback()
        val vm = viewModel(feedback = feedback)
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        assertEquals(listOf(1), feedback.counts)
    }

    @Test
    fun `generate - reports actual number of values on partial batch`() = runTest(dispatcher) {
        // Осталось всего два числа из диапазона, а просим пять.
        repository.create(session(generated = listOf(1, 2, 3, 4, 5, 6, 7, 8)))
        val feedback = RecordingFeedback()
        val vm = viewModel(feedback = feedback)
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        assertEquals(listOf(2), feedback.counts)
        assertTrue(vm.uiState.value.lastBatchPartial)
    }

    @Test
    fun `generate - no feedback when the range is exhausted`() = runTest(dispatcher) {
        repository.create(session(generated = (1..10).toList()))
        val feedback = RecordingFeedback()
        val vm = viewModel(feedback = feedback)
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        assertEquals(emptyList<Int>(), feedback.counts)
    }

    /** Записывает, с каким размером батча вызвали отклик. */
    private class RecordingFeedback : FeedbackProvider {
        val counts = mutableListOf<Int>()

        override fun onGenerated(count: Int) {
            counts += count
        }
    }

    // --- РћС‚РјРµРЅР° РїРѕСЃР»РµРґРЅРµРіРѕ С‡РёСЃР»Р° (1.2) ---

    @Test
    fun `undoLast - removes last number and updates lastNumber`() = runTest(dispatcher) {
        repository.create(session(generated = listOf(3, 7)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.undoLast()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(listOf(3), updated.generated)
        assertEquals(3, vm.uiState.value.lastNumber)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `undoLast - empty history does nothing`() = runTest(dispatcher) {
        repository.create(session())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.undoLast()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.session!!.generated.isEmpty())
        assertEquals(emptyList<Int>(), repository.getSession("s1")!!.generated)
    }

    // --- Р СѓС‡РЅРѕРµ СЂРµРґР°РєС‚РёСЂРѕРІР°РЅРёРµ РёСЃС‚РѕСЂРёРё (1.6) ---

    @Test
    fun `removeNumber - removes by index and saves`() = runTest(dispatcher) {
        repository.create(session(generated = listOf(5, 9, 2)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.removeNumber(1)
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(listOf(5, 2), updated.generated)
        assertEquals(2, vm.uiState.value.lastNumber)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `removeNumber - invalid index is ignored`() = runTest(dispatcher) {
        repository.create(session(generated = listOf(5)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.removeNumber(42)
        advanceUntilIdle()

        assertEquals(listOf(5), vm.uiState.value.session!!.generated)
        assertEquals(listOf(5), repository.getSession("s1")!!.generated)
    }

    @Test
    fun `addManualNumber - valid number appended and saved`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 10, generated = listOf(5)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.addManualNumber(7)
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(listOf(5, 7), updated.generated)
        assertEquals(7, vm.uiState.value.lastNumber)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `addManualNumber - out of range is ignored`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 10, generated = listOf(5)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.addManualNumber(50)
        advanceUntilIdle()

        assertEquals(listOf(5), vm.uiState.value.session!!.generated)
        assertEquals(listOf(5), repository.getSession("s1")!!.generated)
    }

    @Test
    fun `addManualNumber - duplicate without repeats is ignored`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 10, generated = listOf(5)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.addManualNumber(5)
        advanceUntilIdle()

        assertEquals(listOf(5), vm.uiState.value.session!!.generated)
    }

    // --- Последовательность операций (P0.3) ---

    @Test
    fun `generate - rapid taps keep every number`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 100, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        // 20 нажатий подряд без промежуточного advanceUntilIdle.
        repeat(20) { vm.generate() }
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(20, updated.generated.size)
        assertEquals(20, updated.generated.toSet().size)
        // Хранилище содержит ровно ту же историю — ничего не потеряно.
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `generate and undo - operations applied in order`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 100, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        vm.generate()
        vm.generate()
        vm.undoLast()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(2, updated.generated.size)
        assertEquals(updated, repository.getSession("s1"))
    }

    // --- Удалённая сессия не воскресает (P0.4) ---

    @Test
    fun `generate after session deleted - emits SessionNotFound and does not resurrect`() =
        runTest(dispatcher) {
            repository.create(session())
            val vm = viewModel()
            vm.loadSession("s1")
            advanceUntilIdle()

            val events = mutableListOf<SessionEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.events.collect { events.add(it) }
            }
            repository.delete("s1")

            vm.generate()
            advanceUntilIdle()

            assertEquals(listOf<SessionEvent>(SessionEvent.SessionNotFound), events)
            assertNull(repository.getSession("s1"))
        }

    @Test
    fun `undoLast after session deleted - does not resurrect`() = runTest(dispatcher) {
        repository.create(session(generated = listOf(3, 7)))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        repository.delete("s1")
        vm.undoLast()
        advanceUntilIdle()

        assertNull(repository.getSession("s1"))
    }

    // --- Статистика ---

    @Test
    fun `generate - stats are updated after generation`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 100, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.stats.count)

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        val stats = vm.uiState.value.stats
        assertEquals(5, stats.count)
        assertEquals(5, stats.unique)
        assertEquals(0, stats.duplicates)
        assertNotNull(stats.average)
    }

    @Test
    fun `resetNumbers - stats are cleared`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 10, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.stats.count > 0)

        vm.resetNumbers()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.stats.count)
        assertNull(vm.uiState.value.stats.average)
    }

    @Test
    fun `toggleStats - flips the visibility flag`() = runTest(dispatcher) {
        repository.create(session())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showStats)

        vm.toggleStats()
        assertTrue(vm.uiState.value.showStats)
        vm.toggleStats()
        assertFalse(vm.uiState.value.showStats)
    }

    // --- Seed (воспроизводимость) ---

    @Test
    fun `setSeed - persists the seed in the session`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 50, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setSeed(1234L)
        advanceUntilIdle()

        assertEquals(1234L, vm.uiState.value.session!!.seed)
        assertEquals(1234L, repository.getSession("s1")!!.seed)
    }

    @Test
    fun `setSeed null - clears the seed`() = runTest(dispatcher) {
        repository.create(session(seed = 1234L))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setSeed(null)
        advanceUntilIdle()

        assertNull(vm.uiState.value.session!!.seed)
    }

    @Test
    fun `same seed - two sessions produce identical sequences`() = runTest(dispatcher) {
        repository.create(session(id = "a", min = 1, max = 50, allowRepeats = false, seed = 777L))
        repository.create(session(id = "b", min = 1, max = 50, allowRepeats = false, seed = 777L))

        val vmA = viewModel().also { it.loadSession("a") }
        advanceUntilIdle()
        repeat(5) { vmA.generate() }
        advanceUntilIdle()

        val vmB = viewModel().also { it.loadSession("b") }
        advanceUntilIdle()
        repeat(5) { vmB.generate() }
        advanceUntilIdle()

        assertEquals(vmA.uiState.value.session!!.generated, vmB.uiState.value.session!!.generated)
    }

    @Test
    fun `different seeds - sessions produce different sequences`() = runTest(dispatcher) {
        repository.create(session(id = "a", min = 1, max = 1000, allowRepeats = false, seed = 1L))
        repository.create(session(id = "b", min = 1, max = 1000, allowRepeats = false, seed = 2L))

        val vmA = viewModel().also { it.loadSession("a") }
        advanceUntilIdle()
        repeat(5) { vmA.generate() }
        advanceUntilIdle()

        val vmB = viewModel().also { it.loadSession("b") }
        advanceUntilIdle()
        repeat(5) { vmB.generate() }
        advanceUntilIdle()

        assertNotEquals(vmA.uiState.value.session!!.generated, vmB.uiState.value.session!!.generated)
    }

    // --- Экспорт ---

    @Test
    fun `exportHistory - emits ExportReady with formatted file`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 10, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()

        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.exportHistory(HistoryExporter.Format.CSV)
        advanceUntilIdle()

        val event = events.filterIsInstance<SessionEvent.ExportReady>().single()
        assertTrue(event.text.startsWith("value;at"))
        assertTrue(event.fileName.endsWith(".csv"))
        assertEquals("text/csv", event.mimeType)
        assertFalse(event.share)
    }

    @Test
    fun `exportHistory share - emits Markdown flagged for the share sheet`() = runTest(dispatcher) {
        repository.create(session(min = 1, max = 10, allowRepeats = false))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()

        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { events.add(it) }
        }

        vm.exportHistory(HistoryExporter.Format.MARKDOWN, share = true)
        advanceUntilIdle()

        val event = events.filterIsInstance<SessionEvent.ExportReady>().single()
        assertTrue(event.share)
        assertTrue(event.fileName.endsWith(".md"))
        assertEquals("text/markdown", event.mimeType)
        assertTrue(event.text.startsWith("#"))
    }

    // --- Режим «элемент из списка» (2.1) ---

    @Test
    fun `generate items - picks an element, stores its index and saves`() = runTest(dispatcher) {
        repository.create(itemsSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(1, updated.generated.size)
        // Журнал хранит индекс, но пользователю показывается элемент.
        assertTrue(updated.generated.single() in 0..2)
        assertEquals(updated.generatedDisplay.single(), vm.uiState.value.lastDisplay)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `generate items without repeats - exhausts after all items`() = runTest(dispatcher) {
        repository.create(itemsSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        repeat(3) {
            vm.generate()
            advanceUntilIdle()
        }
        val session = vm.uiState.value.session!!
        assertEquals(3, session.generated.size)
        assertEquals(setOf(0, 1, 2), session.generated.toSet())
        assertTrue(vm.uiState.value.showExhaustedDialog)

        // Дальнейшие попытки ничего не добавляют.
        vm.generate()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.session!!.generated.size)
    }

    @Test
    fun `generate items - batch of two picks two distinct items`() = runTest(dispatcher) {
        repository.create(itemsSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(2)
        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(2, updated.generated.size)
        assertEquals(2, updated.generated.toSet().size)
    }

    @Test
    fun `generate items - stats stay empty`() = runTest(dispatcher) {
        repository.create(itemsSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.stats.count)
    }

    // --- Режим «кубики» (2.2) ---

    @Test
    fun `generate dice - appends one entry per die and shows the sum`() = runTest(dispatcher) {
        repository.create(diceSession(count = 3, sides = 6))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(3, updated.generated.size)
        assertTrue(updated.generated.all { it in 1..6 })
        // Крупное значение — сумма броска, а не отдельный кубик.
        assertEquals(updated.generated.sum().toString(), vm.uiState.value.lastDisplay)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `generate dice - repeated rolls are allowed and never exhaust`() = runTest(dispatcher) {
        repository.create(diceSession(count = 1, sides = 2))
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        repeat(10) {
            vm.generate()
            advanceUntilIdle()
        }

        assertEquals(10, vm.uiState.value.session!!.generated.size)
        assertFalse(vm.uiState.value.showExhaustedDialog)
    }

    @Test
    fun `generate dice - same seed reproduces the same rolls`() = runTest(dispatcher) {
        repository.create(diceSession(id = "a", count = 5, sides = 20, seed = 777L))
        repository.create(diceSession(id = "b", count = 5, sides = 20, seed = 777L))

        val vmA = viewModel().also { it.loadSession("a") }
        advanceUntilIdle()
        vmA.generate()
        advanceUntilIdle()

        val vmB = viewModel().also { it.loadSession("b") }
        advanceUntilIdle()
        vmB.generate()
        advanceUntilIdle()

        assertEquals(vmA.uiState.value.session!!.generated, vmB.uiState.value.session!!.generated)
    }

    // --- Режим «монета» (2.3) ---

    @Test
    fun `generate coin - appends one side and shows it`() = runTest(dispatcher) {
        repository.create(coinSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(1, updated.generated.size)
        assertTrue(updated.generated.single() in 0..1)
        assertTrue(vm.uiState.value.lastDisplay in listOf("Орёл", "Решка"))
        assertEquals(1, updated.coinHeadsCount + updated.coinTailsCount)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `generate coin - batch flips several coins`() = runTest(dispatcher) {
        repository.create(coinSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(3)
        vm.generate()
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.session!!.generated.size)
    }

    @Test
    fun `generate coin - same seed reproduces the same flips`() = runTest(dispatcher) {
        repository.create(coinSession(id = "a", seed = 777L))
        repository.create(coinSession(id = "b", seed = 777L))

        val vmA = viewModel().also { it.loadSession("a") }
        advanceUntilIdle()
        repeat(5) {
            vmA.generate()
            advanceUntilIdle()
        }

        val vmB = viewModel().also { it.loadSession("b") }
        advanceUntilIdle()
        repeat(5) {
            vmB.generate()
            advanceUntilIdle()
        }

        assertEquals(vmA.uiState.value.session!!.generated, vmB.uiState.value.session!!.generated)
    }

    // --- Режим «перемешивание» (2.4) ---

    @Test
    fun `generate shuffle - appends a full permutation of indices and saves`() = runTest(dispatcher) {
        repository.create(shuffleSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        val updated = vm.uiState.value.session!!
        assertEquals(3, updated.generated.size)
        assertEquals(setOf(0, 1, 2), updated.generated.toSet())
        assertEquals(updated.lastShuffle!!.joinToString(" → "), vm.uiState.value.lastDisplay)
        assertEquals(updated, repository.getSession("s1"))
    }

    @Test
    fun `generate shuffle - batch size does not multiply the permutation`() = runTest(dispatcher) {
        repository.create(shuffleSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.setBatchSize(5)
        vm.generate()
        advanceUntilIdle()

        // Одно нажатие — одна перестановка независимо от размера батча.
        assertEquals(3, vm.uiState.value.session!!.generated.size)
    }

    @Test
    fun `generate shuffle - repeated shuffles are allowed and never exhaust`() = runTest(dispatcher) {
        repository.create(shuffleSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        repeat(5) {
            vm.generate()
            advanceUntilIdle()
        }

        assertEquals(15, vm.uiState.value.session!!.generated.size)
        assertFalse(vm.uiState.value.showExhaustedDialog)
    }

    @Test
    fun `generate shuffle - same seed reproduces the same order`() = runTest(dispatcher) {
        repository.create(shuffleSession(id = "a", seed = 777L))
        repository.create(shuffleSession(id = "b", seed = 777L))

        val vmA = viewModel().also { it.loadSession("a") }
        advanceUntilIdle()
        vmA.generate()
        advanceUntilIdle()

        val vmB = viewModel().also { it.loadSession("b") }
        advanceUntilIdle()
        vmB.generate()
        advanceUntilIdle()

        assertEquals(vmA.uiState.value.session!!.generated, vmB.uiState.value.session!!.generated)
    }

    @Test
    fun `generate shuffle - stats stay empty`() = runTest(dispatcher) {
        repository.create(shuffleSession())
        val vm = viewModel()
        vm.loadSession("s1")
        advanceUntilIdle()

        vm.generate()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.stats.count)
    }
}
