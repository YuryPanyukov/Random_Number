package CodeSyS.Random_Number.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray

/**
 * JSON-кодек сессий для хранилища.
 *
 * Чистая (не-Android) логика — покрывается unit-тестами.
 *
 * Обратная совместимость: до появления журнала с таймстампами история
 * хранилась плоским списком в поле `generated`. Такие данные читаются
 * и превращаются в журнал с нулевым временем (см. [normalizeLegacy]).
 */
object SessionCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Имя поля с плоской историей чисел (старый формат). */
    private const val LEGACY_GENERATED = "generated"

    /** Имя поля с журналом (текущий формат). */
    private const val LOG = "log"

    /** Сериализует список сессий в JSON-строку для хранилища. */
    fun encode(sessions: List<Session>): String = json.encodeToString(sessions)

    /**
     * Декодирует список сессий.
     *
     * @return пустой список, если [raw] пуст или данные повреждены.
     * Для хранилища используйте [decodeResult]: пустой список и
     * «прочитать нечего» — разные ситуации.
     */
    fun decode(raw: String?): List<Session> = when (val result = decodeResult(raw)) {
        is DecodeResult.Success -> result.sessions
        DecodeResult.Empty, is DecodeResult.Corrupted -> emptyList()
    }

    /**
     * Декодирует список сессий с указанием, что именно произошло:
     * данных нет, данные разобраны или данные повреждены.
     */
    fun decodeResult(raw: String?): DecodeResult = when {
        raw.isNullOrBlank() -> DecodeResult.Empty

        else -> runCatching { parseSessions(raw) }
            .fold(
                onSuccess = { DecodeResult.Success(it) },
                onFailure = { DecodeResult.Corrupted(raw, it) },
            )
    }

    /** Сериализует одну сессию (запись хранилища «ключ → сессия»). */
    fun encodeOne(session: Session): String = json.encodeToString(session)

    /**
     * Декодирует одну сессию.
     *
     * @return `null`, если [raw] пуст или не разбирается.
     */
    fun decodeOne(raw: String?): Session? {
        if (raw.isNullOrBlank()) return null
        return runCatching { parseSession(raw) }.getOrNull()
    }

    /** Сериализует список идентификаторов сессий (индекс хранилища). */
    fun encodeIds(ids: Collection<String>): String = json.encodeToString(ids.toList())

    /**
     * Декодирует индекс идентификаторов сессий.
     *
     * @return `null`, если индекс отсутствует или повреждён — тогда
     * хранилище восстанавливает его по фактическим ключам.
     */
    fun decodeIds(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
    }

    private fun parseSessions(raw: String): List<Session> {
        val array = json.parseToJsonElement(raw).jsonArray
        return array.mapNotNull { element ->
            runCatching { parseElement(element) }.getOrNull()
        }
    }

    private fun parseSession(raw: String): Session = parseElement(json.parseToJsonElement(raw))

    private fun parseElement(element: JsonElement): Session =
        json.decodeFromJsonElement(Session.serializer(), normalizeLegacy(element))

    /**
     * Превращает старое поле `generated: [Int]` в новый `log: [{value, at}]`.
     *
     * Время в таких записях неизвестно, поэтому проставляется `0`;
     * уже корректные данные не меняются.
     */
    private fun normalizeLegacy(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        if (LOG in obj) return element
        val legacy = obj[LEGACY_GENERATED] as? JsonArray ?: return element
        val log = buildJsonArray {
            legacy.forEach { item ->
                val value = (item as? JsonPrimitive)?.intOrNull ?: return@forEach
                add(
                    buildJsonObject {
                        put("value", JsonPrimitive(value))
                        put("at", JsonPrimitive(0L))
                    },
                )
            }
        }
        return JsonObject(obj.toMutableMap().apply { put(LOG, log) })
    }

    /** Результат разбора JSON-строки со списком сессий. */
    sealed interface DecodeResult {
        /** Данных ещё нет (пустое хранилище). */
        data object Empty : DecodeResult

        /** Данные разобраны. */
        data class Success(val sessions: List<Session>) : DecodeResult

        /** Данные есть, но не разбираются — [raw] сохранён для диагностики. */
        data class Corrupted(val raw: String, val cause: Throwable) : DecodeResult
    }
}
