package CodeSyS.Random_Number.ui.home

import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.data.SessionTransfer
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** События главного экрана, требующие действия UI. */
sealed interface HomeEvent {
    /** Подготовлен файл для сохранения всех сессий. */
    data class ExportReady(
        val text: String,
        val fileName: String,
        val mimeType: String,
    ) : HomeEvent

    /** Импортировано [count] сессий. */
    data class Imported(val count: Int) : HomeEvent

    /** Файл импорта не удалось разобрать. */
    data object ImportFailed : HomeEvent
}

/** Состояние главного экрана: список сохранённых сессий. */
data class HomeUiState(
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel главного экрана: список сохранённых сессий,
 * удаление, а также импорт/экспорт сессий.
 */
class HomeViewModel(
    private val repository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository
        .observeSessions()
        .map { sessions -> HomeUiState(sessions = sessions, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val _importedSessions = MutableStateFlow(0)
    val importedSessions: StateFlow<Int> = _importedSessions.asStateFlow()

    /** Удаляет сессию с данным [id] из хранилища. */
    fun deleteSession(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Готовит файл со всеми сессиями для сохранения. */
    fun exportAllSessions(exportedAt: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val sessions = repository.observeSessionsOnce()
            _events.tryEmit(
                HomeEvent.ExportReady(
                    text = SessionTransfer.export(sessions, exportedAt),
                    fileName = "random-number-sessions.json",
                    mimeType = "application/json",
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
}
