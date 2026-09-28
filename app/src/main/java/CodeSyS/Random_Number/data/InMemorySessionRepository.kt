package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory реализация [SessionRepository].
 *
 * Используется в unit-тестах и как запасной вариант,
 * если постоянное хранилище недоступно.
 */
class InMemorySessionRepository(
    initial: List<Session> = emptyList(),
) : SessionRepository {

    private val sessions = MutableStateFlow(initial.sortedByDescending { it.lastUsedAt })

    override fun observeSessions(): Flow<List<Session>> = sessions.asStateFlow()

    override suspend fun getSession(id: String): Session? =
        sessions.value.firstOrNull { it.id == id }

    override suspend fun save(session: Session) {
        sessions.update { list ->
            (list.filterNot { it.id == session.id } + session)
                .sortedByDescending { it.lastUsedAt }
        }
    }

    override suspend fun delete(id: String) {
        sessions.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun clearNumbers(id: String, at: Long): Session? {
        var cleared: Session? = null
        sessions.update { list ->
            val index = list.indexOfFirst { it.id == id }
            if (index == -1) return@update list
            val updated = list[index].withClearedNumbers(at)
            cleared = updated
            list.toMutableList()
                .also { it[index] = updated }
                .sortedByDescending { it.lastUsedAt }
        }
        return cleared
    }
}
