package CodeSyS.Random_Number.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/**
 * Постоянное хранилище настроек на основе Preferences DataStore.
 *
 * Повреждённые/отсутствующие значения заменяются дефолтами
 * ([AppSettings.DEFAULT]) — настройки не должны ронять приложение.
 */
class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val accentKey = stringPreferencesKey("accent")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")
    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val feedbackPerNumberKey = booleanPreferencesKey("feedback_per_number")

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[themeKey].toEnumOrDefault(AppSettings.DEFAULT.themeMode),
            accent = prefs[accentKey].toEnumOrDefault(AppSettings.DEFAULT.accent),
            vibrationEnabled = prefs[vibrationKey] ?: AppSettings.DEFAULT.vibrationEnabled,
            soundEnabled = prefs[soundKey] ?: AppSettings.DEFAULT.soundEnabled,
            feedbackPerNumber = prefs[feedbackPerNumberKey] ?: AppSettings.DEFAULT.feedbackPerNumber,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    override suspend fun setAccent(accent: AccentColor) {
        context.settingsDataStore.edit { it[accentKey] = accent.name }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[vibrationKey] = enabled }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[soundKey] = enabled }
    }

    override suspend fun setFeedbackPerNumber(enabled: Boolean) {
        context.settingsDataStore.edit { it[feedbackPerNumberKey] = enabled }
    }
}

/** Восстанавливает значение enum из строки или возвращает [default]. */
private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
