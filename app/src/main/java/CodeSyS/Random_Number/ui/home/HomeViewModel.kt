package CodeSyS.Random_Number.ui.home

import CodeSyS.Random_Number.data.DefaultSessionTexts
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.data.SessionTexts
import CodeSyS.Random_Number.data.SessionTransfer
import CodeSyS.Random_Number.platform.HonestDraw
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** События главного экрана, требующие действия UI. */
sealed interface HomeEvent {
    /**
     * Подготовлен файл со всеми сессиями.
     *
     * @param share `true` — содержимое нужно отдать в share-sheet, а не
     * сохранять в файл.
     */
    data class ExportReady(
        val text: String,
        val fileName: String,
        val mimeType: String,
        val share: Boolean = false,
    ) : HomeEvent

    /** Импортировано [count] сессий. */
    data class Imported(val count: Int) : HomeEvent

    /** Файл импорта не удалось разобрать. */
    data object ImportFailed : HomeEvent

    /**
     * Честный розыгрыш проведён — [session] содержит результат, seed и
     * источник. UI собирает текст шаринга ([buildShareText]) из этой сессии.
     */
    data class HonestDrawReady(val session: Session) : HomeEvent

    /** Честный розыгрыш не удалось сохранить. */
    data object HonestDrawFailed : HomeEvent

    /** Сессия продублирована: настройки скопированы, история пустая. */
    data object SessionDuplicated : HomeEvent
}

/** Фильтр списка сессий на главном экране (3.6). */
enum class SessionFilter {
    /** Обычные сессии без архива. */
    ALL,

    /** Только избранные (не архивные). */
    FAVORITES,

    /** Только архивные. */
    ARCHIVE,
}

/** Сортировка списка сессий (3.7). */
enum class SessionSort {
    /** По времени последнего использования (новые сверху). */
    DATE,

    /** По заголовку (в текущей локали). */
    TITLE,

    /** По прогрессу: больше выбранных значений — выше. */
    PROGRESS,
}

/** Состояние главного экрана: список сессий и параметры поиска/фильтра (3.5–3.7). */
data class HomeUiState(
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: SessionFilter = SessionFilter.ALL,
    val sort: SessionSort = SessionSort.DATE,
) {
    /** `true`, если задан поиск или выбран непустой фильтр (нет результатов). */
    val hasActiveSearch: Boolean
        get() = query.isNotBlank() || filter != SessionFilter.ALL
}

/** Внутренние параметры списка (поиск, фильтр, сортировка). */
private data class HomeControls(
    val query: String = "",
    val filter: SessionFilter = SessionFilter.ALL,
    val sort: SessionSort = SessionSort.DATE,
)

/**
 * ViewModel главного экрана: список сохранённых сессий, поиск/фильтр/сортировка,
 * избранное/архив/теги, удаление, а также импорт/экспорт сессий.
 */
class HomeViewModel(
    private val repository: SessionRepository,
    private val honestDraw: HonestDraw = HonestDraw(repository),
    private val texts: SessionTexts = DefaultSessionTexts,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val controls = MutableStateFlow(HomeControls())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeSessions(),
        controls,
    ) { sessions, controls ->
        HomeUiState(
            sessions = sessions.applyControls(controls, texts),
            isLoading = false,
            query = controls.query,
            filter = controls.filter,
            sort = controls.sort,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val _importedSessions = MutableStateFlow(0)
    val importedSessions: StateFlow<Int> = _importedSessions.asStateFlow()

    /** Меняет строку поиска по заголовку и тегам. */
    fun setQuery(query: String) {
        controls.update { it.copy(query = query) }
    }

    /** Выбирает фильтр списка (все / избранное / архив). */
    fun setFilter(filter: SessionFilter) {
        controls.update { it.copy(filter = filter) }
    }

    /** Выбирает сортировку списка. */
    fun setSort(sort: SessionSort) {
        controls.update { it.copy(sort = sort) }
    }

    /** Переключает избранное сессии [id]. */
    fun toggleFavorite(id: String) = updateSession(id) { it.withFavorite(!it.isFavorite) }

    /** Переключает архив сессии [id]. */
    fun toggleArchived(id: String) = updateSession(id) { it.withArchived(!it.isArchived) }

    /** Заменяет теги сессии [id] (нормализуются в [Session.withTags]). */
    fun setTags(id: String, tags: List<String>) = updateSession(id) { it.withTags(tags) }

    /**
     * Проводит честный розыгрыш числа 1..100 в один тап: создаёт сессию
     * с seed из SecureRandom, сразу разыгрывает число и эмитит результат
     * для шаринга.
     */
    fun drawHonestNumber() {
        viewModelScope.launch { emitHonestDraw(honestDraw.drawNumber()) }
    }

    /**
     * Проводит честный розыгрыш одного участника из списка [items].
     *
     * @see drawHonestNumber
     */
    fun drawHonestFromList(items: List<String>) {
        viewModelScope.launch { emitHonestDraw(honestDraw.drawItem(items)) }
    }

    /** Эмитит событие по результату розыгрыша ([session] либо ошибку). */
    private fun emitHonestDraw(session: Session?) {
        _events.tryEmit(
            if (session != null) {
                HomeEvent.HonestDrawReady(session)
            } else {
                HomeEvent.HonestDrawFailed
            },
        )
    }

    /** Удаляет сессию с данным [id] из хранилища. */
    fun deleteSession(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /**
     * Дублирует сессию [id]: копия с теми же настройками и пустой историей.
     *
     * Копия создаётся с новым id; при успехе эмитится
     * [HomeEvent.SessionDuplicated] для подтверждения в UI.
     */
    fun duplicateSession(id: String) {
        viewModelScope.launch {
            val session = repository.getSession(id) ?: return@launch
            if (repository.create(session.duplicate(clock()))) {
                _events.tryEmit(HomeEvent.SessionDuplicated)
            }
        }
    }

    /**
     * Готовит файл со всеми сессиями.
     *
     * @param share `true` — отдать содержимое в share-sheet, иначе —
     * предложить сохранить в файл.
     */
    fun exportAllSessions(exportedAt: Long = System.currentTimeMillis(), share: Boolean = false) {
        viewModelScope.launch {
            val sessions = repository.observeSessionsOnce()
            _events.tryEmit(
                HomeEvent.ExportReady(
                    text = SessionTransfer.export(sessions, exportedAt),
                    fileName = "random-number-sessions.json",
                    mimeType = "application/json",
                    share = share,
                ),
            )
        }
    }

    /**
     * Импортирует сессии из файла переноса [raw].
     *
     * Каждой импортированной сессии выдаётся новый id — иначе импорт
     * перезаписал бы уже существующую сессию с тем же id.
     *
     * @return число импортированных сессий.
     */
    suspend fun importSessions(raw: String): Int {
        val sessions = SessionTransfer.read(raw)
        if (sessions == null || sessions.isEmpty()) {
            _events.tryEmit(HomeEvent.ImportFailed)
            return 0
        }
        var imported = 0
        sessions.forEach { session ->
            val copy = session.copy(id = UUID.randomUUID().toString())
            if (repository.create(copy)) imported++
        }
        _importedSessions.value = imported
        _events.tryEmit(HomeEvent.Imported(imported))
        return imported
    }

    /** Применяет к сессии [transform] и сохраняет её (если ещё существует). */
    private fun updateSession(id: String, transform: (Session) -> Session) {
        viewModelScope.launch {
            val session = repository.getSession(id) ?: return@launch
            repository.update(transform(session))
        }
    }
}

/** Применяет поиск/фильтр/сортировку к списку сессий. */
private fun List<Session>.applyControls(
    controls: HomeControls,
    texts: SessionTexts,
): List<Session> {
    val filtered = filter { matches(it, controls, texts) }
    return when (controls.sort) {
        SessionSort.DATE -> filtered.sortedByDescending { it.lastUsedAt }
        SessionSort.TITLE -> filtered.sortedBy { texts.title(it).lowercase() }
        SessionSort.PROGRESS -> filtered.sortedWith(
            compareByDescending<Session> { it.pickedCount }.thenByDescending { it.lastUsedAt },
        )
    }
}

/** Проверяет, попадает ли сессия под фильтр и строку поиска. */
private fun matches(session: Session, controls: HomeControls, texts: SessionTexts): Boolean {
    val filterOk = when (controls.filter) {
        SessionFilter.ALL -> !session.isArchived
        SessionFilter.FAVORITES -> session.isFavorite && !session.isArchived
        SessionFilter.ARCHIVE -> session.isArchived
    }
    if (!filterOk) return false

    val query = controls.query.trim()
    if (query.isEmpty()) return true
    return texts.title(session).contains(query, ignoreCase = true) ||
        session.tags.any { it.contains(query, ignoreCase = true) }
}
