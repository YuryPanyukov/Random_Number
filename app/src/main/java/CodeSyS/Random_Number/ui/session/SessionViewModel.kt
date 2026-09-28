package CodeSyS.Random_Number.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.domain.GenerationResult
import CodeSyS.Random_Number.domain.NumberGenerator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Одноразовые события навигации с экрана сессии. */
sealed interface SessionEvent {
    /** Сессия не найдена (удалена) — вернуться в главное меню. */
    data object SessionNotFound : SessionEvent
}

/** Состояние экрана генерации. */
data class SessionUiState(
    /** Текущая сессия; `null`, пока загружается или если не найдена. */
    val session: Session? = null,
    /** Последнее сгенерированное число (для крупного отображения). */
    val lastNumber: Int? = null,
    /** Показывать диалог «Все числа выбраны». */
    val showExhaustedDialog: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * ViewModel сессии генерации: хранит параметры, выдаёт числа,
 * сбрасывает ранее выбранные и отслеживает исчерпание диапазона.
 *
 * @param clock источник времени — для детерминированных тестов.
 */
class SessionViewModel(
    private val repository: SessionRepository,
    private val generator: NumberGenerator,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Загружает сессию из хранилища (например, при открытии экрана). */
    fun loadSession(id: String) {
        viewModelScope.launch {
            val session = repository.getSession(id)
            if (session == null) {
                _events.tryEmit(SessionEvent.SessionNotFound)
            } else {
                _uiState.value = SessionUiState(
                    session = session,
                    lastNumber = session.generated.lastOrNull(),
                    isLoading = false,
                )
            }
        }
    }

    /** Генерирует следующее число. При исчерпании диапазона показывает диалог. */
    fun generate() {
        val session = _uiState.value.session ?: return
        when (val result = generator.next(
            min = session.min,
            max = session.max,
            allowRepeats = session.allowRepeats,
            generated = session.generated,
        )) {
            is GenerationResult.Success -> {
                val updated = session.withGenerated(result.number, at = clock())
                _uiState.update {
                    it.copy(
                        session = updated,
                        lastNumber = result.number,
                        // Сразу сообщаем, если этим числом диапазон исчерпан.
                        showExhaustedDialog = updated.isExhausted,
                    )
                }
                viewModelScope.launch { repository.save(updated) }
            }

            GenerationResult.Exhausted -> _uiState.update {
                it.copy(showExhaustedDialog = true)
            }
        }
    }

    /** Скидывает ранее выбранные числа («продолжить, но сбросить»). */
    fun resetNumbers() {
        val id = _uiState.value.session?.id ?: return
        viewModelScope.launch {
            val cleared = repository.clearNumbers(id, at = clock())
            if (cleared == null) {
                _events.tryEmit(SessionEvent.SessionNotFound)
            } else {
                _uiState.update {
                    it.copy(
                        session = cleared,
                        lastNumber = null,
                        showExhaustedDialog = false,
                    )
                }
            }
        }
    }

    /** Закрывает диалог «Все числа выбраны» (продолжить как есть). */
    fun dismissExhaustedDialog() {
        _uiState.update { it.copy(showExhaustedDialog = false) }
    }
}
