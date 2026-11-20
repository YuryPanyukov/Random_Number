package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Режим темы: следовать системе, всегда светлая или всегда тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Выбранный акцентный цвет палитры. */
enum class AccentColor {
    /** Динамическая тема системы (Android 12+), иначе фирменный синий. */
    DYNAMIC,

    /** Базовая синяя палитра приложения. */
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
    val accent: AccentColor = AccentColor.INDIGO,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val feedbackPerNumber: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

/**
 * Пользовательский пресет, сохранённый из формы новой сессии.
 *
 * Для режима чисел значимы [min]/[max]/[allowRepeats]; для кубиков —
 * [diceCount]/[diceSides]. Название не хранится: отображается из значений
 * («1..100», «2d6»), чтобы не зависеть от локали при переходах.
 */
@Serializable
data class CustomPreset(
    val kind: PresetKind,
    val min: Int = 0,
    val max: Int = 0,
    val allowRepeats: Boolean = false,
    val diceCount: Int = 0,
    val diceSides: Int = 0,
) {
    /** Режим пресета. */
    @Serializable
    enum class PresetKind {
        /** Пресет чисел из диапазона. */
        NUMBERS,

        /** Пресет набора кубиков. */
        DICE,
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

    /** Пользовательские пресеты («сохранить пресет», приоритет EiE). */
    val customPresets: Flow<List<CustomPreset>>

    /** Добавляет пользовательский пресет; дубликаты не сохраняются. */
    suspend fun addCustomPreset(preset: CustomPreset)

    /** Удаляет пользовательский пресет по индексу списка [customPresets]. */
    suspend fun removeCustomPresetAt(index: Int)

    /** `true`, если демо-сессии первого запуска уже создавались. */
    val demoSeeded: Flow<Boolean>

    /** Помечает демо-сессии первого запуска созданными. */
    suspend fun setDemoSeeded()
}
