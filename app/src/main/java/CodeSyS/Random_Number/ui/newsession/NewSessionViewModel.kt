package CodeSyS.Random_Number.ui.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel экрана параметров новой сессии: создаёт сессию в хранилище
 * и сообщает навигатору её id.
 */
class NewSessionViewModel(
    private val repository: SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _createdSessionId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Одноразовое событие: сессия создана, можно открывать экран генерации. */
    val createdSessionId: SharedFlow<String> = _createdSessionId.asSharedFlow()

    /**
     * Создаёт сессию с параметрами [min]..[max] и сохраняет её;
     * после сохранения эмитится её id ([createdSessionId]).
     *
     * @throws IllegalArgumentException если `min > max`.
     */
    fun createSession(min: Int, max: Int, allowRepeats: Boolean) {
        require(min <= max) { "Некорректный диапазон: min=$min > max=$max" }
        viewModelScope.launch {
            val session = Session.new(
                min = min,
                max = max,
                allowRepeats = allowRepeats,
                now = clock(),
            )
            repository.save(session)
            _createdSessionId.tryEmit(session.id)
        }
    }
}
