package CodeSyS.Random_Number.ui.settings

import CodeSyS.Random_Number.data.AccentColor
import CodeSyS.Random_Number.data.AppSettings
import CodeSyS.Random_Number.data.SettingsRepository
import CodeSyS.Random_Number.data.ThemeMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана настроек: тема, акцент, вибрация/звук.
 *
 * Настройки сразу сохраняются в хранилище ([SettingsRepository]).
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings.DEFAULT,
        )

    fun setThemeMode(mode: ThemeMode) = update { repository.setThemeMode(mode) }

    fun setAccent(accent: AccentColor) = update { repository.setAccent(accent) }

    fun setVibrationEnabled(enabled: Boolean) = update { repository.setVibrationEnabled(enabled) }

    fun setSoundEnabled(enabled: Boolean) = update { repository.setSoundEnabled(enabled) }

    fun setFeedbackPerNumber(enabled: Boolean) = update { repository.setFeedbackPerNumber(enabled) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
