package CodeSyS.Random_Number.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Состояние главного экрана: список сохранённых сессий. */
data class HomeUiState(
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel главного экрана: список сохранённых сессий
 * и их удаление.
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

    /** Удаляет сессию с данным [id] из хранилища. */
    fun deleteSession(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
