package CodeSyS.Random_Number.ui.session

import CodeSyS.Random_Number.data.DefaultSessionTexts
import CodeSyS.Random_Number.data.HistoryExporter
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.data.SessionTexts
import CodeSyS.Random_Number.domain.BatchGenerationResult
import CodeSyS.Random_Number.domain.CoinFlipper
import CodeSyS.Random_Number.domain.DiceRoller
import CodeSyS.Random_Number.domain.ItemGenerationResult
import CodeSyS.Random_Number.domain.ItemGenerator
import CodeSyS.Random_Number.domain.NumberGenerator
import CodeSyS.Random_Number.domain.RandomSourceMode
import CodeSyS.Random_Number.domain.SessionStats
import CodeSyS.Random_Number.domain.Shuffler
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

    /**
     * Подготовлен файл для сохранения — [text] и имя файла [fileName].
     *
     * @param share `true` — содержимое нужно отдать в share-sheet, а не
     * сохранять в файл.
     */
    data class ExportReady(
        val text: String,
        val fileName: String,
        val mimeType: String,
        val share: Boolean = false,
    ) : SessionEvent
}

/** Состояние экрана генерации. */
data class SessionUiState(
    /** Текущая сессия; `null`, пока загружается или если не найдена. */
    val session: Session? = null,
    /** Последнее сгенерированное число (для крупного отображения). */
    val lastNumber: Int? = null,
    /** Последнее значение для показа: число либо элемент списка. */
    val lastDisplay: String? = null,
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
    /** Показывать диалог редактирования сессии (3.2). */
    val showEditDialog: Boolean = false,
    /** Текст названия в диалоге редактирования. */
    val editTitleText: String = "",
    /** Текст заметки в диалоге редактирования. */
    val editDescriptionText: String = "",
    /** Текст диапазона «от» в диалоге редактирования (режим чисел). */
    val editMinText: String = "",
    /** Текст диапазона «до» в диалоге редактирования (режим чисел). */
    val editMaxText: String = "",
    /** Флаг «разрешить повторы» в диалоге редактирования. */
    val editAllowRepeats: Boolean = false,
    /** Текст элементов в диалоге редактирования (режим списка/перемешивания). */
    val editItemsText: String = "",
    /** Текст «сколько кубиков» в диалоге редактирования (режим кубиков). */
    val editDiceCountText: String = "",
    /** Текст «сколько граней» в диалоге редактирования (режим кубиков). */
    val editDiceSidesText: String = "",
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
    private val itemGenerator: ItemGenerator = ItemGenerator(),
    private val diceRoller: DiceRoller = DiceRoller(),
    private val coinFlipper: CoinFlipper = CoinFlipper(),
    private val shuffler: Shuffler = Shuffler(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val feedback: FeedbackProvider? = null,
    private val randomMode: RandomSourceMode = RandomSourceMode.DEFAULT,
    /** Локализованные строки (заголовок, стороны монеты, экспорт). */
    private val texts: SessionTexts = DefaultSessionTexts,
    /** Вызывается после каждого изменения истории — обновляет виджет (5.4). */
    private val onHistoryChanged: () -> Unit = {},
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
                lastDisplay = session.lastDisplay(texts),
                stats = statsFor(session),
                isLoading = false,
            )
        }
    }

    /**
     * Меняет количество чисел, генерируемых за одно нажатие (≥ 1).
     *
     * Меняет состояние напрямую (мимо очереди операций) — безопасно,
     * так как это чистый UI-флаг, не влияющий на данные сессии.
     *
     * Соглашение: мимо очереди можно менять только чистые UI-флаги
     * (`batchSize`, `showStats`, `showExhaustedDialog`), которые не
     * мутруют данные сессии и не требуют атомарности с другими операциями.
     * Любая логика, затрагивающая `Session`, должна идти через `enqueue`.
     */
    fun setBatchSize(count: Int) {
        _uiState.update { it.copy(batchSize = count.coerceAtLeast(1)) }
    }

    /**
     * Генерирует следующее число (или батч из [SessionUiState.batchSize]
     * чисел). При исчерпании диапазона показывает диалог.
     */
    fun generate() = enqueue {
        val session = session ?: return@enqueue this
        if (session.isItemsMode) return@enqueue generateItem(session)
        if (session.isDiceMode) return@enqueue generateDice(session)
        if (session.isCoinMode) return@enqueue generateCoin(session)
        if (session.isShuffleMode) return@enqueue generateShuffle(session)

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
                    lastDisplay = updated.lastDisplay(texts),
                    // Сразу сообщаем, если этим батчем диапазон исчерпан.
                    showExhaustedDialog = updated.isExhausted,
                    lastBatchPartial = result.partial,
                    stats = statsFor(updated),
                )
            }

            // Частичное исчерпание — просто вернулись оставшиеся числа
            // (result.numbers непуст, иначе был бы Exhausted).
            BatchGenerationResult.Exhausted -> copy(showExhaustedDialog = true)
        }
    }

    /**
     * Генерирует элемент(ы) списка (режим [SessionPayload.Items]).
     *
     * Журнал хранит индексы выпавших элементов — так прогресс и исчерпание
     * работают теми же полями, что и у режима чисел (см. [Session]).
     */
    private suspend fun SessionUiState.generateItem(session: Session): SessionUiState {
        val result = itemGeneratorFor(session).next(
            count = batchSize,
            items = session.items,
            allowRepeats = session.allowRepeats,
            generated = session.generated,
        )
        return when (result) {
            is ItemGenerationResult.Success -> {
                val updated = session.withGenerated(result.indices, at = clock())
                if (!persist(updated)) {
                    this
                } else {
                    feedback?.onGenerated(result.items.size)
                    copy(
                        session = updated,
                        lastNumber = result.indices.last(),
                        lastDisplay = updated.lastDisplay(texts),
                        showExhaustedDialog = updated.isExhausted,
                        lastBatchPartial = result.partial,
                    )
                }
            }

            ItemGenerationResult.Exhausted -> copy(showExhaustedDialog = true)
        }
    }

    /**
     * Бросает кубики (режим [SessionPayload.Dice]).
     *
     * Каждое значение кубика — отдельная запись журнала; прогресс и
     * исчерпание не применимы (кубики кидаются повторно).
     */
    private suspend fun SessionUiState.generateDice(session: Session): SessionUiState {
        val payload = session.payload as SessionPayload.Dice
        val roll = diceRollerFor(session).roll(count = payload.count, sides = payload.sides)
        val updated = session.withGenerated(roll.dice, at = clock())
        if (!persist(updated)) return this
        feedback?.onGenerated(roll.dice.size)
        return copy(
            session = updated,
            lastNumber = roll.sum,
            lastDisplay = updated.lastDisplay(texts),
            stats = statsFor(updated),
        )
    }

    /**
     * Бросает монету (режим [SessionPayload.Coin]).
     *
     * Размер батча работает как обычно: можно бросить сразу несколько
     * монет. В журнал пишутся стороны как `0`/`1`.
     */
    private suspend fun SessionUiState.generateCoin(session: Session): SessionUiState {
        val values = coinFlipperFor(session).flip(batchSize).map { it.ordinal }
        val updated = session.withGenerated(values, at = clock())
        if (!persist(updated)) return this
        feedback?.onGenerated(values.size)
        return copy(
            session = updated,
            lastNumber = values.last(),
            lastDisplay = updated.lastDisplay(texts),
            stats = statsFor(updated),
        )
    }

    /**
     * Перемешивает список (режим [SessionPayload.Shuffle], задача 2.4).
     *
     * Одно нажатие даёт новую перестановку всех элементов; в журнал
     * пишутся индексы в порядке перемешивания, поэтому история, экспорт
     * и вывод работают теми же механизмами, что у списка.
     */
    private suspend fun SessionUiState.generateShuffle(session: Session): SessionUiState {
        val payload = session.payload as SessionPayload.Shuffle
        val indices = shufflerFor(session).shuffle(payload.items.indices.toList())
        val updated = session.withGenerated(indices, at = clock())
        if (!persist(updated)) return this
        feedback?.onGenerated(1)
        return copy(
            session = updated,
            lastNumber = indices.last(),
            lastDisplay = updated.lastDisplay(texts),
        )
    }

    /** Убирает последнее число истории («отменить последнее»). */
    fun undoLast() = enqueue {
        val session = session ?: return@enqueue this
        if (session.log.isEmpty()) return@enqueue this
        val updated = session.withoutLastGenerated(at = clock())
        if (!persist(updated)) return@enqueue this
        // Крупное значение — последнее в истории (или null, если пусто).
        copy(
            session = updated,
            lastNumber = updated.lastNumber,
            lastDisplay = updated.lastDisplay(texts),
            stats = statsFor(updated),
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
            lastDisplay = updated.lastDisplay(texts),
            stats = statsFor(updated),
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
        copy(
            session = updated,
            lastNumber = number,
            lastDisplay = updated.lastDisplay(texts),
            stats = statsFor(updated),
        )
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
                lastDisplay = null,
                showExhaustedDialog = false,
                stats = SessionStats.EMPTY,
            )
        }
    }

    /**
     * Показывает/скрывает блок статистики.
     *
     * Меняет состояние напрямую (мимо очереди) — чистый UI-флаг.
     * См. соглашение в [setBatchSize].
     */
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

    /**
     * Подготавливает историю сессии в выбранном формате.
     *
     * @param share `true` — отдать содержимое в share-sheet, иначе —
     * предложить сохранить в файл.
     */
    fun exportHistory(format: HistoryExporter.Format, share: Boolean = false) = enqueue {
        val session = session ?: return@enqueue this
        _events.tryEmit(
            SessionEvent.ExportReady(
                text = HistoryExporter.export(session, format, texts),
                fileName = HistoryExporter.fileName(format, clock()),
                mimeType = format.mimeType,
                share = share,
            ),
        )
        this
    }

    /**
     * Закрывает диалог «Все числа выбраны» (продолжить как есть).
     *
     * Меняет состояние напрямую (мимо очереди) — чистый UI-флаг.
     * См. соглашение в [setBatchSize].
     */
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

    /** Генератор элементов — с тем же правилом seed, что и у чисел. */
    private fun itemGeneratorFor(session: Session): ItemGenerator =
        if (session.seed != null) {
            ItemGenerator(createRandom(randomMode, session.seed))
        } else {
            itemGenerator
        }

    /** Бросатель кубиков — с тем же правилом seed, что и у чисел. */
    private fun diceRollerFor(session: Session): DiceRoller =
        if (session.seed != null) {
            DiceRoller(createRandom(randomMode, session.seed))
        } else {
            diceRoller
        }

    /** Бросатель монеты — с тем же правилом seed, что и у чисел. */
    private fun coinFlipperFor(session: Session): CoinFlipper =
        if (session.seed != null) {
            CoinFlipper(createRandom(randomMode, session.seed))
        } else {
            coinFlipper
        }

    /** Перемешиватель — с тем же правилом seed, что и у чисел. */
    private fun shufflerFor(session: Session): Shuffler =
        if (session.seed != null) {
            Shuffler(createRandom(randomMode, session.seed))
        } else {
            shuffler
        }

    /** Статистика считается только для чисел: у списка индексы не значимы. */
    private fun statsFor(session: Session): SessionStats =
        if (session.isItemsMode || session.isShuffleMode) {
            SessionStats.EMPTY
        } else {
            computeSessionStats(session.generated)
        }

    /**
     * Сохраняет [updated] в хранилище.
     *
     * @return `false`, если сессию успели удалить — тогда эмитится
     * [SessionEvent.SessionNotFound] и состояние не меняется.
     */
    private suspend fun persist(updated: Session): Boolean {
        if (!repository.update(updated)) {
            _events.tryEmit(SessionEvent.SessionNotFound)
            return false
        }
        // Виджет показывает снимок последней сессии — обновляем его.
        onHistoryChanged()
        return true
    }

    /** Ставит операцию в очередь; её результат попадёт в [uiState]. */
    private fun enqueue(operation: suspend SessionUiState.() -> SessionUiState) {
        operations.trySend(operation)
    }

    /**
     * Открывает диалог редактирования сессии (3.2).
     *
     * Мимо очереди: только заполнение UI-полей, не мутует данные.
     */
    fun showEditDialog() {
        val session = uiState.value.session ?: return
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editTitleText = session.title,
                editDescriptionText = session.description,
                editAllowRepeats = session.allowRepeats,
                editItemsText = session.items.joinToString("\n"),
                editDiceCountText = (session.payload as? SessionPayload.Dice)?.count.toString(),
                editDiceSidesText = (session.payload as? SessionPayload.Dice)?.sides.toString(),
            ).let { state ->
                val p = session.payload
                when {
                    p is SessionPayload.Numbers ->
                        state.copy(
                            editMinText = p.min.toString(),
                            editMaxText = p.max.toString(),
                        )

                    p is SessionPayload.Dice -> state

                    else -> state
                }
            }
        }
    }

    /** Закрывает диалог редактирования без сохранения. */
    fun hideEditDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }

    /** Сохраняет изменения из диалога редактирования (3.2). */
    fun saveEdit() {
        val session = uiState.value.session ?: return
        val state = uiState.value
        val title = state.editTitleText.trim().takeIf { it.isNotEmpty() } ?: session.title
        val description = state.editDescriptionText
        val updated = session.withTitle(title).withDescription(description)

        val p = session.payload
        val final = when (p) {
            is SessionPayload.Numbers -> {
                val min = state.editMinText.toIntOrNull() ?: p.min
                val max = state.editMaxText.toIntOrNull() ?: p.max
                if (min <= max) updated.withNumbersRange(min, max, state.editAllowRepeats)
                else updated
            }

            is SessionPayload.Items, is SessionPayload.Shuffle -> {
                val items = state.editItemsText.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
                if (items.isNotEmpty()) updated.withItems(items, state.editAllowRepeats)
                else updated
            }

            is SessionPayload.Dice -> {
                val count = state.editDiceCountText.toIntOrNull()?.coerceAtLeast(1) ?: p.count
                val sides = state.editDiceSidesText.toIntOrNull()?.coerceAtLeast(2) ?: p.sides
                updated.copy(payload = SessionPayload.Dice(count, sides))
            }

            is SessionPayload.Coin -> updated
        }

        viewModelScope.launch {
            repository.update(final)
        }
        hideEditDialog()
    }

    /**
     * Сохраняет заметку к сессии (3.4).
     *
     * Мимо очереди: заметка — чистый текст, не влияет на генерацию.
     * См. соглашение в [setBatchSize].
     */
    fun setDescription(description: String) {
        val session = uiState.value.session ?: return
        val updated = session.withDescription(description)
        viewModelScope.launch {
            repository.update(updated)
        }
    }
}
