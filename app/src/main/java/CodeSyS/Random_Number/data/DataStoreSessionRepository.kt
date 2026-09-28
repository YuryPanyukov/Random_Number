package CodeSyS.Random_Number.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sessions",
)

/**
 * Постоянное хранилище сессий на основе Preferences DataStore.
 *
 * Список сессий хранится одной JSON-строкой ([SessionCodec]);
 * сессии всегда отдаются отсортированными по убыванию [Session.lastUsedAt].
 *
 * Read-modify-write операции защищены [Mutex], т.к. `DataStore.edit`
 * атомарен только в пределах одной транзакции.
 */
class DataStoreSessionRepository(
    private val context: Context,
) : SessionRepository {

    private val mutex = Mutex()
    private val sessionsKey = stringPreferencesKey("sessions_json")

    override fun observeSessions(): Flow<List<Session>> =
        context.sessionDataStore.data
            .map { prefs -> SessionCodec.decode(prefs[sessionsKey]) }

    override suspend fun getSession(id: String): Session? =
        observeSessions().first().firstOrNull { it.id == id }

    override suspend fun save(session: Session) {
        mutex.withLock {
            context.sessionDataStore.edit { prefs ->
                val current = SessionCodec.decode(prefs[sessionsKey])
                val updated = (current.filterNot { it.id == session.id } + session)
                    .sortedByDescending { it.lastUsedAt }
                prefs[sessionsKey] = SessionCodec.encode(updated)
            }
        }
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            context.sessionDataStore.edit { prefs ->
                val updated = SessionCodec.decode(prefs[sessionsKey])
                    .filterNot { it.id == id }
                prefs[sessionsKey] = SessionCodec.encode(updated)
            }
        }
    }

    override suspend fun clearNumbers(id: String, at: Long): Session? {
        return mutex.withLock {
            var cleared: Session? = null
            context.sessionDataStore.edit { prefs ->
                val current = SessionCodec.decode(prefs[sessionsKey])
                val index = current.indexOfFirst { it.id == id }
                if (index != -1) {
                    val updated = current[index].withClearedNumbers(at)
                    cleared = updated
                    prefs[sessionsKey] = SessionCodec.encode(
                        current.toMutableList().also { it[index] = updated },
                    )
                }
            }
            cleared
        }
    }
}
