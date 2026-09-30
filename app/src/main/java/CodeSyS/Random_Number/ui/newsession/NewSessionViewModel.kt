package CodeSyS.Random_Number.ui.newsession

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние формы новой сессии.
 *
 * Текст полей хранится строкой (как в поле ввода), разобранные значения
 * и ошибки валидации — производные от него.
 */
data class NewSessionUiState(
    val minText: String = DEFAULT_MIN,
    val maxText: String = DEFAULT_MAX,
    /** Значение переключателя «Без повторений»: `true` — повторов не будет. */
    val withoutRepeats: Boolean = true,
    /** Поля уже трогали пользователем — до этого ошибки не показываем. */
    val minTouched: Boolean = false,
    val maxTouched: Boolean = false,
) {
    /** Минимальное значение диапазона или `null`, если введено не число. */
    val min: Int? get() = minText.toIntOrNull()

    /** Максимальное значение диапазона или `null`, если введено не число. */
    val max: Int? get() = maxText.toIntOrNull()

    /** `true`, если «от» больше, чем «до». */
    val rangeError: Boolean get() = min != null && max != null && min!! > max!!

    /** `true`, если «от» введено некорректно (и поле трогали). */
    val minError: Boolean get() = minTouched && min == null

    /** `true`, если «до» введено некорректно (и поле трогали). */
    val maxError: Boolean get() = maxTouched && max == null

    /** Можно ли создать сессию с текущим вводом. */
    val canCreate: Boolean get() = min != null && max != null && !rangeError

    /** Размер диапазона `min..max` или `0`, если ввод некорректен. */
    val rangeSize: Long
        get() = if (canCreate) max!!.toLong() - min!! + 1L else 0L

    /** Применяет параметры пресета: диапазон и режим повторов. */
    fun withPreset(min: Int, max: Int, allowRepeats: Boolean): NewSessionUiState = copy(
        minText = min.toString(),
        maxText = max.toString(),
        withoutRepeats = !allowRepeats,
        minTouched = true,
        maxTouched = true,
    )

    companion object {
        const val DEFAULT_MIN = "1"
        const val DEFAULT_MAX = "100"
    }
}

/**
 * ViewModel экрана параметров новой сессии: валидирует введённые данные,
 * создаёт сессию в хранилище и сообщает навигатору её id.
 *
 * Состояние формы живёт здесь, а не в композабле: при повороте экрана
 * (или смене конфигурации) введённые значения сохраняются.
 *
 * @param clock источник времени — для детерминированных тестов.
 */
class NewSessionViewModel(
    private val repository: SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSessionUiState())
    val uiState: StateFlow<NewSessionUiState> = _uiState.asStateFlow()

    private val _createdSessionId = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Одноразовое событие: сессия создана, можно открывать экран генерации. */
    val createdSessionId: SharedFlow<String> = _createdSessionId.asSharedFlow()

    fun setMin(value: String) {
        _uiState.update { it.copy(minText = value, minTouched = true) }
    }

    fun setMax(value: String) {
        _uiState.update { it.copy(maxText = value, maxTouched = true) }
    }

    fun setWithoutRepeats(value: Boolean) {
        _uiState.update { it.copy(withoutRepeats = value) }
    }

    /** Применяет пресет диапазона одним тапом. */
    fun applyPreset(min: Int, max: Int, allowRepeats: Boolean) {
        _uiState.update { it.withPreset(min, max, allowRepeats) }
    }

    /**
     * Создаёт сессию по текущему состоянию формы.
     *
     * @return `false`, если ввод некорректен (сессия не создана).
     */
    fun createSession(): Boolean {
        val state = _uiState.value
        val min = state.min
        val max = state.max
        if (min == null || max == null || state.rangeError) return false
        viewModelScope.launch {
            val session = Session.new(
                min = min,
                max = max,
                allowRepeats = !state.withoutRepeats,
                now = clock(),
            )
            if (!repository.create(session)) return@launch
            _createdSessionId.tryEmit(session.id)
        }
        return true
    }

    /**
     * Создаёт сессию с явными параметрами (используется в тестах
     * и как тонкая обёртка над состоянием формы).
     */
    fun createSession(min: Int, max: Int, allowRepeats: Boolean): Boolean {
        require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }
        _uiState.update {
            it.copy(minText = min.toString(), maxText = max.toString(), withoutRepeats = !allowRepeats)
        }
        return createSession()
    }
}
