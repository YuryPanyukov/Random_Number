package CodeSyS.Random_Number.data

/**
 * Раскладка сессий в хранилище «ключ → строка» + общие правила чтения/записи.
 *
 * Каждая сессия лежит отдельным ключом (`session_<id>`), список id — в
 * ключе [INDEX_KEY]. Это дешевле, чем переписывание одного большого JSON
 * на каждое изменение, и выносит правила сортировки/валидации в одно
 * место: обе реализации [SessionRepository] используют этот объект.
 *
 * Ключевые гарантии:
 * - запись одной сессии не трогает остальные (в том числе повреждённые);
 * - повреждённая запись не затирается молча — она пропускается при чтении
 *   и возвращается вызывающему коду через `onCorrupt`;
 * - повреждённый/отсутствующий индекс восстанавливается по ключам;
 * - данные старого формата (одна JSON-строка) читаются и переносятся
 *   в новый формат (см. [migrateLegacy]);
 * - порядок выдачи всегда `lastUsedAt` по убыванию.
 */
object SessionStorage {

    /** Ключ со списком id сессий (JSON-массив строк). */
    const val INDEX_KEY: String = "sessions_index"

    /** Ключ со старым форматом: все сессии одной JSON-строкой. */
    const val LEGACY_KEY: String = "sessions_json"

    private const val ENTRY_PREFIX = "session_"

    private fun entryKey(id: String): String = "$ENTRY_PREFIX$id"

    /**
     * Читает все сессии, отсортированные по убыванию `lastUsedAt`.
     *
     * @param onCorrupt вызывается для каждой нераспознанной записи
     * (id и исходная строка). Сама запись при этом не удаляется и не
     * перезаписывается — «починить» её можно вручную.
     */
    fun readAll(
        store: StringKeyValueStore,
        onCorrupt: (id: String, raw: String) -> Unit = { _, _ -> },
    ): List<Session> {
        val entries = readIds(store).mapNotNull { id ->
            val raw = store.get(entryKey(id))
            if (raw == null) {
                null
            } else {
                SessionCodec.decodeOne(raw) ?: run {
                    onCorrupt(id, raw)
                    null
                }
            }
        }
        // Новый формат пуст, но старые данные ещё не перенесены —
        // читаем их на месте, чтобы пользователь ничего не потерял.
        val sessions = if (entries.isEmpty()) readLegacy(store) else entries
        return sessions.sortedByDescending { it.lastUsedAt }
    }

    /** Читает одну сессию по [id] или `null`, если её нет/запись повреждена. */
    fun getById(store: StringKeyValueStore, id: String): Session? =
        SessionCodec.decodeOne(store.get(entryKey(id)))
            ?: readLegacy(store).firstOrNull { it.id == id }

    /**
     * Создаёт сессию [session].
     *
     * @return `false`, если сессия с таким id уже существует
     * (существующая запись не перезаписывается).
     */
    fun create(store: StringKeyValueStore, session: Session): Boolean {
        // Незавершённая миграция старого формата не должна приводить
        // к «воскрешению» уже существующей сессии.
        migrateLegacy(store)
        if (getById(store, session.id) != null) return false
        write(store, session)
        return true
    }

    /**
     * Обновляет уже существующую сессию [session].
     *
     * @return `false`, если сессии с таким id в хранилище нет —
     * удалённая сессия не «воскресает» повторным сохранением.
     */
    fun update(store: StringKeyValueStore, session: Session): Boolean {
        migrateLegacy(store)
        if (getById(store, session.id) == null) return false
        write(store, session)
        return true
    }

    /** Создаёт или обновляет сессию без проверки существования (старый upsert). */
    fun upsert(store: StringKeyValueStore, session: Session) {
        write(store, session)
    }

    /** Удаляет сессию [id] вместе с её записью. */
    fun delete(store: StringKeyValueStore, id: String) {
        store.remove(entryKey(id))
        // Индекс может ещё содержать удалённый id — выкидываем его явно.
        val remaining = readIds(store).filterNot { it == id }
        store.put(INDEX_KEY, SessionCodec.encodeIds(remaining))
        removeFromLegacy(store, id)
    }

    /**
     * Сбрасывает ранее сгенерированные числа сессии [id].
     *
     * @return обновлённая сессия или `null`, если её нет.
     */
    fun clearNumbers(store: StringKeyValueStore, id: String, at: Long): Session? {
        migrateLegacy(store)
        val current = getById(store, id) ?: return null
        val cleared = current.withClearedNumbers(at)
        write(store, cleared)
        return cleared
    }

    /**
     * Переносит данные из старого формата (одна JSON-строка) в новый
     * (запись на сессию + индекс). Вызывается автоматически при первой
     * же записи, поэтому отдельного шага миграции не требуется.
     *
     * @return `true`, если перенесена хотя бы одна сессия.
     */
    fun migrateLegacy(store: StringKeyValueStore): Boolean {
        val legacy = readLegacy(store)
        if (legacy.isEmpty()) return false
        val existing = readIds(store)
        val toMove = legacy.filterNot { it.id in existing }
        if (toMove.isNotEmpty()) {
            toMove.forEach { store.put(entryKey(it.id), SessionCodec.encodeOne(it)) }
        }
        val left = legacy.filter { it.id in existing }
        if (left.isEmpty()) {
            store.remove(LEGACY_KEY)
        } else {
            store.put(LEGACY_KEY, SessionCodec.encode(left))
        }
        writeIndex(store, toMove.map { it.id })
        return toMove.isNotEmpty()
    }

    /** Сессии из старого формата; повреждённые данные дают пустой список. */
    private fun readLegacy(store: StringKeyValueStore): List<Session> {
        val raw = store.get(LEGACY_KEY) ?: return emptyList()
        return SessionCodec.decode(raw)
    }

    /** Убирает сессию из невыполненной миграции старого формата. */
    private fun removeFromLegacy(store: StringKeyValueStore, id: String) {
        val remaining = readLegacy(store).filterNot { it.id == id }
        if (remaining.isEmpty()) {
            store.remove(LEGACY_KEY)
        } else {
            store.put(LEGACY_KEY, SessionCodec.encode(remaining))
        }
    }

    /** Записывает сессию и обновляет индекс. */
    private fun write(store: StringKeyValueStore, session: Session) {
        store.put(entryKey(session.id), SessionCodec.encodeOne(session))
        writeIndex(store, listOf(session.id))
    }

    /** Дополняет индекс новыми id (восстанавливает, если индекс пропал). */
    private fun writeIndex(store: StringKeyValueStore, added: Collection<String>) {
        val ids = readIds(store)
        val merged = (ids + added).distinct()
        if (merged != ids || store.get(INDEX_KEY) == null) {
            store.put(INDEX_KEY, SessionCodec.encodeIds(merged))
        }
    }

    /**
     * Идентификаторы сессий: из индекса, а при его отсутствии или
     * повреждении — по фактическим ключам хранилища.
     *
     * Метод только читает (индекс восстанавливается при следующей записи),
     * поэтому пригоден и для потока чтения без прав записи.
     */
    private fun readIds(store: StringKeyValueStore): List<String> {
        val stored = store.keys().filter { it.startsWith(ENTRY_PREFIX) }
            .map { it.removePrefix(ENTRY_PREFIX) }
        val fromIndex = SessionCodec.decodeIds(store.get(INDEX_KEY))
            ?: return stored.sorted()
        // Записи, о которых индекс не знает, — не теряем: дописываем.
        return (fromIndex + stored).distinct()
    }
}
