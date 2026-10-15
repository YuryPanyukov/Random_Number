package CodeSyS.Random_Number.data

/**
 * Минимальное хранилище «ключ → строка».
 *
 * Нужно, чтобы раскладку сессий ([SessionStorage]) можно было тестировать
 * на обычной JVM без Android: DataStore отдаёт [androidx.datastore.preferences.core.Preferences]
 * (только чтение) и [androidx.datastore.preferences.core.MutablePreferences]
 * (чтение + запись), а тестам достаточно обычной mutable-карты.
 */
interface StringKeyValueStore {

    /** Значение по [key] или `null`, если ключа нет. */
    fun get(key: String): String?

    /** Все ключи хранилища. */
    fun keys(): Set<String>

    /** Записывает значение по [key]. */
    fun put(key: String, value: String)

    /** Удаляет ключ [key] (если его нет — ничего не делает). */
    fun remove(key: String)
}

/** [StringKeyValueStore] поверх обычной изменяемой карты (тесты, [InMemorySessionRepository]). */
class MapKeyValueStore(
    initial: Map<String, String> = emptyMap(),
) : StringKeyValueStore {

    private val values: MutableMap<String, String> = LinkedHashMap(initial)

    /** Текущее содержимое хранилища (копия — для проверок в тестах). */
    fun snapshot(): Map<String, String> = values.toMap()

    override fun get(key: String): String? = values[key]

    override fun keys(): Set<String> = values.keys.toSet()

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
