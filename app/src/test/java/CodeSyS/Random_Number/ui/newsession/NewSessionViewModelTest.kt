package CodeSyS.Random_Number.ui.newsession

import CodeSyS.Random_Number.data.InMemorySessionRepository
import CodeSyS.Random_Number.data.SessionPayload
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
class NewSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: InMemorySessionRepository
    private var now = 5_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemorySessionRepository()
        now = 5_000L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(mode: NewSessionMode = NewSessionMode.NUMBERS) =
        NewSessionViewModel(repository, clock = { now }, initialMode = mode)

    /** Сессии, сохранённые в хранилище (актуально после `advanceUntilIdle`). */
    private suspend fun storedSessions() = repository.observeSessions().first()

    // --- Валидация формы ---

    @Test
    fun `initial state - default range 1-100 without repeats`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertEquals("1", state.minText)
        assertEquals("100", state.maxText)
        assertTrue(state.withoutRepeats)
        assertTrue(state.canCreate)
        assertEquals(100L, state.rangeSize)
    }

    @Test
    fun `setMin with non numeric text - marks error and blocks creation`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMin("abc")

        val state = vm.uiState.value
        assertTrue(state.minError)
        assertFalse(state.canCreate)
        assertFalse(vm.createSession())
        advanceUntilIdle()
        assertTrue(storedSessions().isEmpty())
    }

    @Test
    fun `setMax less than min - range error blocks creation`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMin("50")
        vm.setMax("10")

        val state = vm.uiState.value
        assertTrue(state.rangeError)
        assertFalse(state.canCreate)
        assertFalse(vm.createSession())
        advanceUntilIdle()
        assertTrue(storedSessions().isEmpty())
    }

    @Test
    fun `errors are not shown before the field is touched`() {
        val state = NewSessionUiState()

        assertFalse(state.minError)
        assertFalse(state.maxError)
    }

    @Test
    fun `setMin and setMax - update parsed values`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMin("-5")
        vm.setMax("5")

        val state = vm.uiState.value
        assertEquals(-5, state.min)
        assertEquals(5, state.max)
        assertEquals(11L, state.rangeSize)
    }

    // --- Пресеты ---

    @Test
    fun `applyPreset - fills range and toggles repeats`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.applyPreset(min = 1, max = 6, allowRepeats = true)

        val state = vm.uiState.value
        assertEquals("1", state.minText)
        assertEquals("6", state.maxText)
        // Повторы разрешены → переключатель «Без повторений» выключен.
        assertFalse(state.withoutRepeats)
        assertTrue(state.canCreate)
    }

    @Test
    fun `applyPreset without repeats - enables the switch`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setWithoutRepeats(false)

        vm.applyPreset(min = 1, max = 45, allowRepeats = false)

        assertTrue(vm.uiState.value.withoutRepeats)
    }

    // --- Создание ---

    @Test
    fun `createSession - stores session and emits id`() = runTest(dispatcher) {
        val vm = viewModel()
        val ids = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.createdSessionId.collect { ids.add(it) }
        }
        vm.setMin("1")
        vm.setMax("6")
        vm.setWithoutRepeats(false) // повторы разрешены

        assertTrue(vm.createSession())
        advanceUntilIdle()

        val stored = storedSessions().single()
        assertEquals(1, stored.min)
        assertEquals(6, stored.max)
        assertTrue(stored.allowRepeats)
        assertEquals(listOf(stored.id), ids)
    }

    @Test
    fun `createSession - without repeats flag maps to allowRepeats`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMin("1")
        vm.setMax("10")
        vm.setWithoutRepeats(true)

        vm.createSession()
        advanceUntilIdle()

        assertFalse(storedSessions().single().allowRepeats)
    }

    @Test
    fun `createSession with explicit params - fills the form`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.createSession(min = 1, max = 45, allowRepeats = false)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("1", state.minText)
        assertEquals("45", state.maxText)
        assertEquals(1, storedSessions().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createSession with explicit invalid range throws`() = runTest(dispatcher) {
        viewModel().createSession(min = 10, max = 1, allowRepeats = true)
    }

    // --- Режим «элемент из списка» (2.1) ---

    @Test
    fun `items mode - canCreate requires a non empty list`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMode(NewSessionMode.ITEMS)
        vm.setItemsText("  \n \n")
        assertFalse(vm.uiState.value.canCreate)

        vm.setItemsText("Аня")
        assertTrue(vm.uiState.value.canCreate)
    }

    @Test
    fun `parsedItems - trims, drops blanks and de-duplicates`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMode(NewSessionMode.ITEMS)

        vm.setItemsText(" Аня \nБорис\n\nАня\n Вера ")

        assertEquals(listOf("Аня", "Борис", "Вера"), vm.uiState.value.parsedItems)
    }

    @Test
    fun `createSession items - stores Items payload and emits id`() = runTest(dispatcher) {
        val vm = viewModel()
        val ids = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.createdSessionId.collect { ids.add(it) }
        }
        vm.setMode(NewSessionMode.ITEMS)
        vm.setItemsText("Аня\nБорис\nВера")
        vm.setWithoutRepeats(true)

        assertTrue(vm.createSession())
        advanceUntilIdle()

        val stored = storedSessions().single()
        assertEquals(
            SessionPayload.Items(
                items = listOf("Аня", "Борис", "Вера"),
                allowRepeats = false,
            ),
            stored.payload,
        )
        assertTrue(stored.isItemsMode)
        assertEquals(listOf(stored.id), ids)
    }

    @Test
    fun `createSession items - empty list creates nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMode(NewSessionMode.ITEMS)

        assertFalse(vm.createSession())
        advanceUntilIdle()

        assertTrue(storedSessions().isEmpty())
    }

    @Test
    fun `applyPreset - switches back to numbers mode`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMode(NewSessionMode.ITEMS)

        vm.applyPreset(min = 1, max = 6, allowRepeats = true)

        assertEquals(NewSessionMode.NUMBERS, vm.uiState.value.mode)
    }

    // --- Режим «кубики» (2.2) ---

    @Test
    fun `dice mode - defaults are 2d6 and valid`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMode(NewSessionMode.DICE)

        val state = vm.uiState.value
        assertEquals(2, state.diceCount)
        assertEquals(6, state.diceSides)
        assertTrue(state.canCreate)
    }

    @Test
    fun `dice mode - invalid count or sides block creation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMode(NewSessionMode.DICE)

        vm.setDiceCount("0")
        assertFalse(vm.uiState.value.canCreate)

        vm.setDiceCount("2")
        vm.setDiceSides("1")
        assertFalse(vm.uiState.value.canCreate)

        vm.setDiceSides("abc")
        assertFalse(vm.uiState.value.canCreate)
    }

    @Test
    fun `createSession dice - stores Dice payload and labels the title`() = runTest(dispatcher) {
        val vm = viewModel()
        val ids = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.createdSessionId.collect { ids.add(it) }
        }
        vm.setMode(NewSessionMode.DICE)
        vm.setDiceCount("3")
        vm.setDiceSides("8")

        assertTrue(vm.createSession())
        advanceUntilIdle()

        val stored = storedSessions().single()
        assertEquals(SessionPayload.Dice(count = 3, sides = 8), stored.payload)
        assertTrue(stored.isDiceMode)
        assertEquals("3d8", stored.title)
        assertEquals(listOf(stored.id), ids)
    }

    // --- Режим «монета» (2.3) ---

    @Test
    fun `coin mode - always creatable and stores Coin payload`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMode(NewSessionMode.COIN)
        assertTrue(vm.uiState.value.canCreate)

        assertTrue(vm.createSession())
        advanceUntilIdle()

        val stored = storedSessions().single()
        assertEquals(SessionPayload.Coin, stored.payload)
        assertTrue(stored.isCoinMode)
        assertEquals("Монета", stored.title)
    }

    // --- Режим «перемешивание» (2.4) ---

    @Test
    fun `shuffle mode - requires at least two items`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setMode(NewSessionMode.SHUFFLE)
        vm.setItemsText("Аня")
        assertFalse(vm.uiState.value.canCreate)

        vm.setItemsText("Аня\nБорис")
        assertTrue(vm.uiState.value.canCreate)
    }

    // --- Предвыбранный режим (экран выбора, 2.6) ---

    @Test
    fun `initial mode - opens the form with the selected mode`() {
        assertEquals(NewSessionMode.COIN, viewModel(NewSessionMode.COIN).uiState.value.mode)
        assertEquals(NewSessionMode.DICE, viewModel(NewSessionMode.DICE).uiState.value.mode)
        assertEquals(
            NewSessionMode.SHUFFLE,
            viewModel(NewSessionMode.SHUFFLE).uiState.value.mode,
        )
    }

    @Test
    fun `initial mode - default is numbers`() {
        assertEquals(NewSessionMode.NUMBERS, viewModel().uiState.value.mode)
    }

    @Test
    fun `initial mode - createSession uses the preselected mode`() = runTest(dispatcher) {
        val vm = viewModel(NewSessionMode.DICE)
        vm.setDiceCount("3")
        vm.setDiceSides("8")

        assertTrue(vm.createSession())
        advanceUntilIdle()

        assertEquals(SessionPayload.Dice(count = 3, sides = 8), storedSessions().single().payload)
    }

    @Test
    fun `createSession shuffle - stores Shuffle payload and labels the title`() = runTest(dispatcher) {
        val vm = viewModel()
        val ids = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.createdSessionId.collect { ids.add(it) }
        }
        vm.setMode(NewSessionMode.SHUFFLE)
        vm.setItemsText("Аня\nБорис\nВера")

        assertTrue(vm.createSession())
        advanceUntilIdle()

        val stored = storedSessions().single()
        assertEquals(
            SessionPayload.Shuffle(items = listOf("Аня", "Борис", "Вера")),
            stored.payload,
        )
        assertTrue(stored.isShuffleMode)
        assertEquals("Перемешивание (3)", stored.title)
        assertEquals(listOf(stored.id), ids)
    }
}
