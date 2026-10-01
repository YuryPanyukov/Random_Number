package CodeSyS.Random_Number.ui.session

import CodeSyS.Random_Number.data.HistoryExporter
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.domain.BatchGenerationResult
import CodeSyS.Random_Number.domain.NumberGenerator
import CodeSyS.Random_Number.domain.RandomSourceMode
import CodeSyS.Random_Number.domain.SessionStats
import CodeSyS.Random_Number.domain.computeSessionStats
import CodeSyS.Random_Number.domain.createRandom
import CodeSyS.Random_Number.platform.FeedbackProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Одноразовые события навигации с экрана сессии. */
sealed interface SessionEvent {
    /** Сессия не найдена (удалена) — вернуться в главное меню. */
    data object SessionNotFound : SessionEvent

    /** Подготовлен файл для сохранения — [text] и имя файла [fileName]. */
    data class ExportReady(
        val text: String,
        val fileName: String,
        val mimeType: String,
    ) : SessionEvent
}

/** Состояние экрана генерации. */
data class SessionUiState(
    /** Текущая сессия; `null`, пока загружается или если не найдена. */
    val session: Session? = null,
    /** Последнее сгенерированное число (для крупного отображения). */
    val lastNumber: Int? = null,
    /** Показывать диалог «Все числа выбраны». */
    val showExhaustedDialog: Boolean = false,
    /** Сколько чисел генерировать за одно нажатие (1/2/3/5/10). */
    val batchSize: Int = 1,
    /**
     * Последний батч выдан частично: просили [batchSize] чисел,
     * а диапазон «без повторений» дал меньше.
     */
    val lastBatchPartial: Boolean = false,
    /** Показывать блок статистики по истории. */
    val showStats: Boolean = false,
    /** Статистика по текущей истории (пустая, если история пуста). */
    val stats: SessionStats = SessionStats.EMPTY,
    val isLoading: Boolean = true,
)

/**
 * ViewModel сессии генерации: хранит параметры, выдаёт числа,
 * сбрасывает ранее выбранные и отслеживает исчерпание диапазона.
 *
 * Все операции, меняющие историю, выполняются **последовательно** в одном
 * обработчике очереди ([operations]): быстрые повторные нажатия не могут
 * перепутать порядок записей и потерять числа.
 *
 * @param clock источник времени — для детерминированных тестов.
 * @param feedback тактильный/звуковой отклик при генерации
 * (`null` — без отклика, например в тестах).
 * @param randomMode источник случайности (обычный или криптографический).
 */
class SessionViewModel(
    private val repository: SessionRepository,
    private val generator: NumberGenerator,
    private val clock: () -> Long = System::currentTimeMillis,
    private val feedback: FeedbackProvider? = null,
    private val randomMode: RandomSourceMode = RandomSourceMode.DEFAULT,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Очередь операций: единственный писатель состояния и хранилища. */
    private val operations = Channel<suspend SessionUiState.() -> SessionUiState>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            operations.consumeAsFlow().collect { operation ->
                _uiState.value = _uiState.value.operation()
            }
        }
    }

    /** Загружает сессию из хранилища (например, при открытии экрана). */
    fun loadSession(id: String) = enqueue {
        val session = repository.getSession(id)
        if (session == null) {
            _events.tryEmit(SessionEvent.SessionNotFound)
            this
        } else {
            SessionUiState(
                session = session,
                lastNumber = session.lastNumber,
                stats = computeSessionStats(session.generated),
                isLoading = false,
            )
        }
    }

    /** Меняет количество чисел, генерируемых за одно нажатие (≥ 1). */
    fun setBatchSize(count: Int) {
        _uiState.update { it.copy(batchSize = count.coerceAtLeast(1)) }
    }

    /**
     * Генерирует следующее число (или батч из [SessionUiState.batchSize]
     * чисел). При исчерпании диапазона показывает диалог.
     */
    fun generate() = enqueue {
        val session = session ?: return@enqueue this
        val source = generatorFor(session)
        val result = source.next(
            count = batchSize,
            min = session.min,
            max = session.max,
            allowRepeats = session.allowRepeats,
            generated = session.generated,
        )
        when (result) {
            is BatchGenerationResult.Success -> {
                val at = clock()
                val updated = session.withGenerated(result.numbers, at = at)
                if (!persist(updated)) return@enqueue this
                feedback?.onGenerated(result.numbers.size)
                copy(
                    session = updated,
                    lastNumber = result.numbers.last(),
                    // Сразу сообщаем, если этим батчем диапазон исчерпан.
                    showExhaustedDialog = updated.isExhausted,
                    lastBatchPartial = result.partial,
                    stats = computeSessionStats(updated.generated),
                )
            }

            // Частичное исчерпание — просто вернулись оставшиеся числа
            // (result.numbers непуст, иначе был бы Exhausted).
            BatchGenerationResult.Exhausted -> copy(showExhaustedDialog = true)
        }
    }

    /** Убирает последнее число истории («отменить последнее»). */
    fun undoLast() = enqueue {
        val session = session ?: return@enqueue this
        if (session.log.isEmpty()) return@enqueue this
        val updated = session.withoutLastGenerated(at = clock())
        if (!persist(updated)) return@enqueue this
        // Крупное число — последнее в истории (или null, если пусто).
        copy(
            session = updated,
            lastNumber = updated.lastNumber,
            stats = computeSessionStats(updated.generated),
        )
    }

    /** Удаляет число истории по индексу. */
    fun removeNumber(index: Int) = enqueue {
        val session = session ?: return@enqueue this
        if (index !in session.log.indices) return@enqueue this
        val updated = session.removeGeneratedAt(index, at = clock())
        if (!persist(updated)) return@enqueue this
        copy(
            session = updated,
            lastNumber = updated.lastNumber,
            stats = computeSessionStats(updated.generated),
        )
    }

    /**
     * Добавляет число в историю вручную с валидацией
     * (диапазон, повторы). Некорректное число игнорируется.
     */
    fun addManualNumber(number: Int) = enqueue {
        val session = session ?: return@enqueue this
        val updated = runCatching { session.addManual(number, at = clock()) }
            .getOrNull() ?: return@enqueue this
        if (!persist(updated)) return@enqueue this
        copy(session = updated, lastNumber = number, stats = computeSessionStats(updated.generated))
    }

    /** Скидывает ранее выбранные числа («продолжить, но сбросить»). */
    fun resetNumbers() = enqueue {
        val id = session?.id ?: return@enqueue this
        val cleared = repository.clearNumbers(id, at = clock())
        if (cleared == null) {
            _events.tryEmit(SessionEvent.SessionNotFound)
            this
        } else {
            copy(
                session = cleared,
                lastNumber = null,
                showExhaustedDialog = false,
                stats = computeSessionStats(emptyList()),
            )
        }
    }

    /** Показывает/скрывает блок статистики. */
    fun toggleStats() {
        _uiState.update { it.copy(showStats = !it.showStats) }
    }

    /**
     * Фиксирует seed сессии (или сбрасывает его при `null`), чтобы
     * генерацию можно было воспроизвести.
     */
    fun setSeed(seed: Long?) = enqueue {
        val session = session ?: return@enqueue this
        val updated = session.withSeed(seed)
        if (!persist(updated)) return@enqueue this
        copy(session = updated)
    }

    /** Подготавливает файл с историей сессии для сохранения. */
    fun exportHistory(format: HistoryExporter.Format) = enqueue {
        val session = session ?: return@enqueue this
        _events.tryEmit(
            SessionEvent.ExportReady(
                text = HistoryExporter.export(session, format),
                fileName = HistoryExporter.fileName(format, clock()),
                mimeType = format.mimeType,
            ),
        )
        this
    }

    /** Закрывает диалог «Все числа выбраны» (продолжить как есть). */
    fun dismissExhaustedDialog() {
        _uiState.update { it.copy(showExhaustedDialog = false) }
    }

    /**
     * Генератор для текущей сессии.
     *
     * Сессия с зафиксированным seed всегда даёт одну и ту же
     * последовательность; без seed берётся переданный в конструктор.
     */
    private fun generatorFor(session: Session): NumberGenerator =
        if (session.seed != null) {
            NumberGenerator(createRandom(randomMode, session.seed))
        } else {
            generator
        }

    /**
     * Сохраняет [updated] в хранилище.
     *
     * @return `false`, если сессию успели удалить — тогда эмитится
     * [SessionEvent.SessionNotFound] и состояние не меняется.
     */
    private suspend fun persist(updated: Session): Boolean {
        if (repository.update(updated)) return true
        _events.tryEmit(SessionEvent.SessionNotFound)
        return false
    }

    /** Ставит операцию в очередь; её результат попадёт в [uiState]. */
    private fun enqueue(operation: suspend SessionUiState.() -> SessionUiState) {
        operations.trySend(operation)
    }
}
