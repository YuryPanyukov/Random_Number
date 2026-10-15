package CodeSyS.Random_Number.ui.settings

import CodeSyS.Random_Number.data.AccentColor
import CodeSyS.Random_Number.data.AppSettings
import CodeSyS.Random_Number.data.SettingsRepository
import CodeSyS.Random_Number.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Держит подписку на stateIn(WhileSubscribed) активной в рамках теста. */
    private fun TestScope.subscribe(vm: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect { }
        }
    }

    @Test
    fun `uiState - initial defaults`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(AppSettings.DEFAULT, vm.uiState.value)
    }

    @Test
    fun `setThemeMode - updates state and repository`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)

        vm.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
    }

    @Test
    fun `setAccent - updates state and repository`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)

        vm.setAccent(AccentColor.TEAL)
        advanceUntilIdle()

        assertEquals(AccentColor.TEAL, vm.uiState.value.accent)
        assertEquals(AccentColor.TEAL, repository.settings.value.accent)
    }

    @Test
    fun `setVibrationEnabled - toggles flag`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)

        vm.setVibrationEnabled(false)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.vibrationEnabled)
        assertEquals(false, repository.settings.value.vibrationEnabled)
    }

    @Test
    fun `setSoundEnabled - toggles flag`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)

        vm.setSoundEnabled(true)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.soundEnabled)
        assertEquals(true, repository.settings.value.soundEnabled)
    }

    @Test
    fun `setFeedbackPerNumber - toggles flag`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)

        vm.setFeedbackPerNumber(true)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.feedbackPerNumber)
        assertEquals(true, repository.settings.value.feedbackPerNumber)
    }

    @Test
    fun `setFeedbackPerNumber - defaults to off`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repository)
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.feedbackPerNumber)
    }

    /** In-memory реализация [SettingsRepository] для тестов. */
    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppSettings.DEFAULT)
        override val settings: StateFlow<AppSettings> = state.asStateFlow()

        override suspend fun setThemeMode(mode: ThemeMode) {
            state.update { it.copy(themeMode = mode) }
        }

        override suspend fun setAccent(accent: AccentColor) {
            state.update { it.copy(accent = accent) }
        }

        override suspend fun setVibrationEnabled(enabled: Boolean) {
            state.update { it.copy(vibrationEnabled = enabled) }
        }

        override suspend fun setSoundEnabled(enabled: Boolean) {
            state.update { it.copy(soundEnabled = enabled) }
        }

        override suspend fun setFeedbackPerNumber(enabled: Boolean) {
            state.update { it.copy(feedbackPerNumber = enabled) }
        }
    }
}
