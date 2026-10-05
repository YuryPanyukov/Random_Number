package CodeSyS.Random_Number.ui.newsession

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
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

/** Режим создаваемой сессии. */
enum class NewSessionMode {
    /** Генерация чисел в заданном диапазоне. */
    NUMBERS,

    /** Выбор случайного элемента из введённого списка (2.1). */
    ITEMS,

    /** Бросок кубиков (2.2). */
    DICE,

    /** Бросок монеты «орёл/решка» (2.3). */
    COIN,

    /** Перемешивание списка (2.4). */
    SHUFFLE,
}

/**
 * Состояние формы новой сессии.
 *
 * Текст полей хранится строкой (как в поле ввода), разобранные значения
 * и ошибки валидации — производные от него.
 */
data class NewSessionUiState(
    val mode: NewSessionMode = NewSessionMode.NUMBERS,
    val minText: String = DEFAULT_MIN,
    val maxText: String = DEFAULT_MAX,
    /** Значение переключателя «Без повторений»: `true` — повторов не будет. */
    val withoutRepeats: Boolean = true,
    /** Поля уже трогали пользователем — до этого ошибки не показываем. */
    val minTouched: Boolean = false,
    val maxTouched: Boolean = false,
    /** Элементы списка режима [NewSessionMode.ITEMS], по одному в строке. */
    val itemsText: String = "",
    /** Число кубиков в броске (режим [NewSessionMode.DICE]). */
    val diceCountText: String = DEFAULT_DICE_COUNT,
    /** Число граней кубика (режим [NewSessionMode.DICE]). */
    val diceSidesText: String = DEFAULT_DICE_SIDES,
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

    /**
     * Элементы списка: непустые строки без учёта порядка, дубликаты
     * удалены (с сохранением порядка первого появления).
     */
    val parsedItems: List<String>
        get() = itemsText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /** Число кубиков или `null`, если введено не число. */
    val diceCount: Int? get() = diceCountText.toIntOrNull()

    /** Число граней или `null`, если введено не число. */
    val diceSides: Int? get() = diceSidesText.toIntOrNull()

    /** Можно ли создать сессию с текущим вводом. */
    val canCreate: Boolean
        get() = when (mode) {
            NewSessionMode.NUMBERS -> min != null && max != null && !rangeError
            NewSessionMode.ITEMS -> parsedItems.isNotEmpty()
            NewSessionMode.DICE -> (diceCount ?: 0) >= 1 && (diceSides ?: 0) >= 2
            NewSessionMode.COIN -> true
            NewSessionMode.SHUFFLE -> parsedItems.size >= 2
        }

    /** Размер диапазона `min..max` или `0`, если ввод некорректен. */
    val rangeSize: Long
        get() = if (canCreate) max!!.toLong() - min!! + 1L else 0L

    /** Применяет параметры пресета: диапазон и режим повторов. */
    fun withPreset(min: Int, max: Int, allowRepeats: Boolean): NewSessionUiState = copy(
        mode = NewSessionMode.NUMBERS,
        minText = min.toString(),
        maxText = max.toString(),
        withoutRepeats = !allowRepeats,
        minTouched = true,
        maxTouched = true,
    )

    companion object {
        const val DEFAULT_MIN = "1"
        const val DEFAULT_MAX = "100"
        const val DEFAULT_DICE_COUNT = "2"
        const val DEFAULT_DICE_SIDES = "6"
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
 * @param initialMode режим, предвыбранный на экране выбора (2.6): форма
 * открывается сразу с нужными полями, без повторного переключения.
 */
class NewSessionViewModel(
    private val repository: SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    initialMode: NewSessionMode = NewSessionMode.NUMBERS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSessionUiState(mode = initialMode))
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

    /** Переключает режим формы: числа диапазона или список элементов. */
    fun setMode(mode: NewSessionMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    /** Обновляет текст списка элементов (режим [NewSessionMode.ITEMS]). */
    fun setItemsText(value: String) {
        _uiState.update { it.copy(itemsText = value) }
    }

    /** Обновляет число кубиков (режим [NewSessionMode.DICE]). */
    fun setDiceCount(value: String) {
        _uiState.update { it.copy(diceCountText = value) }
    }

    /** Обновляет число граней кубика (режим [NewSessionMode.DICE]). */
    fun setDiceSides(value: String) {
        _uiState.update { it.copy(diceSidesText = value) }
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
        if (!state.canCreate) return false

        viewModelScope.launch {
            val session = when (state.mode) {
                NewSessionMode.NUMBERS -> Session.new(
                    min = state.min!!,
                    max = state.max!!,
                    allowRepeats = !state.withoutRepeats,
                    now = clock(),
                )

                NewSessionMode.ITEMS -> Session.new(
                    payload = SessionPayload.Items(
                        items = state.parsedItems,
                        allowRepeats = !state.withoutRepeats,
                    ),
                    now = clock(),
                    title = itemsTitle(state.parsedItems.size, state.withoutRepeats),
                )

                NewSessionMode.DICE -> {
                    val payload = SessionPayload.Dice(
                        count = state.diceCount!!,
                        sides = state.diceSides!!,
                    )
                    Session.new(payload = payload, now = clock(), title = payload.label)
                }

                NewSessionMode.COIN -> Session.new(
                    payload = SessionPayload.Coin,
                    now = clock(),
                    title = coinTitle(),
                )

                NewSessionMode.SHUFFLE -> Session.new(
                    payload = SessionPayload.Shuffle(items = state.parsedItems),
                    now = clock(),
                    title = shuffleTitle(state.parsedItems.size),
                )
            }
            if (!repository.create(session)) return@launch
            _createdSessionId.tryEmit(session.id)
        }
        return true
    }

    /** Заголовок сессии-списка: количество элементов и режим повторов. */
    private fun itemsTitle(count: Int, withoutRepeats: Boolean): String =
        "Список ($count)" + if (withoutRepeats) ", без повторов" else ""

    /** Заголовок сессии-монеты. */
    private fun coinTitle(): String = "Монета"

    /** Заголовок сессии-перемешивания: количество элементов. */
    private fun shuffleTitle(count: Int): String = "Перемешивание ($count)"

    /**
     * Создаёт сессию с явными параметрами (используется в тестах
     * и как тонкая обёртка над состоянием формы).
     */
    fun createSession(min: Int, max: Int, allowRepeats: Boolean): Boolean {
        require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }
        _uiState.update {
            it.copy(
                mode = NewSessionMode.NUMBERS,
                minText = min.toString(),
                maxText = max.toString(),
                withoutRepeats = !allowRepeats,
            )
        }
        return createSession()
    }
}
