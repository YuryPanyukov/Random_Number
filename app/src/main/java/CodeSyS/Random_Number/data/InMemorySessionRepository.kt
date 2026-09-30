package CodeSyS.Random_Number.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory реализация [SessionRepository].
 *
 * Используется в unit-тестах и как запасной вариант,
 * если постоянное хранилище недоступно. Раскладка данных — та же,
 * что и в постоянном хранилище ([SessionStorage]), поэтому обе
 * реализации ведут себя одинаково.
 */
class InMemorySessionRepository(
    initial: List<Session> = emptyList(),
) : SessionRepository {

    private val values = MutableStateFlow(
        MapKeyValueStore().let { store ->
            initial.forEach { SessionStorage.upsert(store, it) }
            store.snapshot()
        },
    )

    private val mutex = Mutex()

    /** Повреждённые записи, о которых сообщило хранилище (для диагностики в тестах). */
    val corrupted: MutableList<Pair<String, String>> = mutableListOf()

    override fun observeSessions(): Flow<List<Session>> =
        values.map { raw -> SessionStorage.readAll(raw.asStore()) { id, text -> corrupted += id to text } }

    override suspend fun getSession(id: String): Session? =
        SessionStorage.getById(values.value.asStore(), id)

    override suspend fun create(session: Session): Boolean = edit { SessionStorage.create(it, session) }

    override suspend fun update(session: Session): Boolean = edit { SessionStorage.update(it, session) }

    override suspend fun delete(id: String) {
        edit { SessionStorage.delete(it, id) }
    }

    override suspend fun clearNumbers(id: String, at: Long): Session? =
        edit { SessionStorage.clearNumbers(it, id, at) }

    /**
     * Прямая запись в хранилище без проверок сортировки/существования —
     * нужна тестам, чтобы подложить повреждённые или устаревшие данные.
     */
    fun putRaw(key: String, value: String) {
        values.value = values.value + (key to value)
    }

    /** Текущее содержимое «сырых» ключей — для проверок в тестах. */
    fun rawKeys(): Set<String> = values.value.keys

    /** Изменение хранилища целиком (как одна транзакция DataStore). */
    private suspend fun <T> edit(block: (MapKeyValueStore) -> T): T = mutex.withLock {
        val copy = MapKeyValueStore(values.value)
        val result = block(copy)
        values.value = copy.snapshot()
        result
    }
}

/** Читаемый снимок [StringKeyValueStore] поверх неизменяемой карты. */
private fun Map<String, String>.asStore(): StringKeyValueStore = object : StringKeyValueStore {
    override fun get(key: String): String? = this@asStore[key]
    override fun keys(): Set<String> = this@asStore.keys
    override fun put(key: String, value: String): Unit =
        error("Хранилище доступно только для чтения")

    override fun remove(key: String): Unit = error("Хранилище доступно только для чтения")
}
