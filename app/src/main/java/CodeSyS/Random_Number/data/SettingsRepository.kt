package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.Flow

/** Режим темы: следовать системе, всегда светлая или всегда тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Выбранный акцентный цвет палитры. */
enum class AccentColor {
    /** Динамическая тема системы (Android 12+), иначе индиго. */
    DYNAMIC,

    /** Базовая индиго-палитра приложения. */
    INDIGO,

    /** Бирюзовый акцент. */
    TEAL,

    /** Розовый акцент. */
    ROSE,
}

/**
 * Настройки приложения (тема, акцент, отклики при генерации).
 *
 * @param themeMode режим темы.
 * @param accent акцентный цвет.
 * @param vibrationEnabled вибрация при генерации.
 * @param soundEnabled звук при генерации.
 * @param feedbackPerNumber отклик на каждое число в батче, а не один
 * сигнал на весь батч. По умолчанию выключено: десяток подряд идущих
 * вибраций сливается в один неразличимый сигнал.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.DYNAMIC,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val feedbackPerNumber: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

/**
 * Хранилище настроек приложения.
 *
 * [settings] эмитит текущее значение при каждой изменении.
 */
interface SettingsRepository {

    /** Текущие настройки. */
    val settings: Flow<AppSettings>

    /** Устанавливает режим темы. */
    suspend fun setThemeMode(mode: ThemeMode)

    /** Устанавливает акцентный цвет. */
    suspend fun setAccent(accent: AccentColor)

    /** Включает/выключает вибрацию при генерации. */
    suspend fun setVibrationEnabled(enabled: Boolean)

    /** Включает/выключает звук при генерации. */
    suspend fun setSoundEnabled(enabled: Boolean)

    /** Включает/выключает отклик на каждое число в батче. */
    suspend fun setFeedbackPerNumber(enabled: Boolean)
}
