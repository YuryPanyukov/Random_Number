package CodeSyS.Random_Number.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex

private const val TAG = "SessionRepository"

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sessions",
)

/**
 * Постоянное хранилище сессий на основе Preferences DataStore.
 *
 * Раскладка данных и правила сортировки — в [SessionStorage]; здесь
 * только адаптация Preferences к [StringKeyValueStore].
 *
 * Особенности:
 * - каждая сессия лежит отдельным ключом, поэтому запись одной сессии
 *   не переписывает остальные и не приближает хранилище к лимиту размера;
 * - повреждённые записи пропускаются при чтении, логируются и **не
 *   затираются** следующей записью;
 * - данные из старого формата (одна JSON-строка) переносятся на лету;
 * - read-modify-write защищены [Mutex].
 */
class DataStoreSessionRepository(
    private val context: Context,
) : SessionRepository {

    override fun observeSessions(): Flow<List<Session>> = data()
        .map { prefs ->
            SessionStorage.readAll(prefs.asReadStore()) { id, raw ->
                Log.e(
                    TAG,
                    "Повреждена запись сессии $id (${raw.length} символов); " +
                        "она пропущена, но не удалена",
                )
            }
        }

    override suspend fun getSession(id: String): Session? =
        SessionStorage.getById(data().first().asReadStore(), id)

    override suspend fun create(session: Session): Boolean = edit { store ->
        migrateIfNeeded(store)
        SessionStorage.create(store, session)
    }

    override suspend fun update(session: Session): Boolean = edit { store ->
        SessionStorage.update(store, session)
    }

    override suspend fun delete(id: String) {
        edit { store -> SessionStorage.delete(store, id) }
    }

    override suspend fun clearNumbers(id: String, at: Long): Session? = edit { store ->
        SessionStorage.clearNumbers(store, id, at)
    }

    /** Поток предпочтений: ошибки чтения файла не роняют приложение. */
    private fun data(): Flow<Preferences> = context.sessionDataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.e(TAG, "Ошибка чтения хранилища сессий", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    /**
     * Атомарное изменение: `DataStore.edit` сам сериализует транзакции,
     * поэтому дополнительный мьютекс не нужен — read-modify-write целиком
     * выполняется внутри одной транзакции.
     */
    private suspend fun <T> edit(block: (StringKeyValueStore) -> T): T {
        var result: T? = null
        context.sessionDataStore.edit { prefs ->
            val store = prefs.asStore()
            migrateIfNeeded(store)
            result = block(store)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Перенос данных из старого формата (одна JSON-строка) в новый. */
    private fun migrateIfNeeded(store: StringKeyValueStore) {
        val raw = store.get(SessionStorage.LEGACY_KEY) ?: return
        val moved = SessionStorage.migrateLegacy(store)
        if (moved) {
            Log.i(TAG, "Выполнена миграция хранилища сессий в новый формат")
        } else {
            Log.e(
                TAG,
                "Не удалось разобрать данные старого формата (${raw.length} символов); " +
                    "значение сохранено без изменений",
            )
        }
    }
}

/** [StringKeyValueStore] поверх Preferences DataStore. */
private fun MutablePreferences.asStore(): StringKeyValueStore = object : StringKeyValueStore {

    override fun get(key: String): String? = this@asStore[stringKey(key)]

    override fun keys(): Set<String> = this@asStore.asMap().keys.map { it.name }.toSet()

    override fun put(key: String, value: String) {
        this@asStore[stringKey(key)] = value
    }

    override fun remove(key: String) {
        this@asStore.remove(stringKey(key))
    }
}

/** Тот же адаптер, но только для чтения (снимок `Preferences`). */
private fun Preferences.asReadStore(): StringKeyValueStore = object : StringKeyValueStore {

    override fun get(key: String): String? = this@asReadStore[stringKey(key)]

    override fun keys(): Set<String> = this@asReadStore.asMap().keys.map { it.name }.toSet()

    override fun put(key: String, value: String): Unit =
        error("Снимок Preferences доступен только для чтения")

    override fun remove(key: String): Unit =
        error("Снимок Preferences доступен только для чтения")
}

private fun stringKey(name: String): Preferences.Key<String> =
    androidx.datastore.preferences.core.stringPreferencesKey(name)
