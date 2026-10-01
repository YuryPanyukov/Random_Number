package CodeSyS.Random_Number.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

/**
 * JSON-кодек сессий для хранилища.
 *
 * Чистая (не-Android) логика — покрывается unit-тестами.
 *
 * Обратная совместимость (два слоя):
 * 1. журнал: до появления таймстампов история хранилась плоским списком
 *    в поле `generated` — такие записи превращаются в журнал с нулевым
 *    временем ([normalizeLog]);
 * 2. payload: до появления режимов поля диапазона лежали в корне объекта
 *    сессии без дискриминатора `type` — они собираются в
 *    `SessionPayload.Numbers` ([normalizePayload]).
 */
object SessionCodec {

    /** Имя дискриминатора payload (`Json { classDiscriminator = ... }`). */
    private const val TYPE = "type"

    /** Значение дискриминатора режима чисел (`@SerialName` в payload). */
    private const val TYPE_NUMBERS = "numbers"

    /** Имя поля с payload сессии. */
    private const val PAYLOAD = "payload"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = TYPE
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
        json.decodeFromJsonElement(Session.serializer(), normalizeSessionJson(element))

    /**
     * Приводит JSON сессии к текущему виду: старые поля собираются
     * в текущие (`generated` → `log`, корневые `min`/`max`/`allowRepeats`
     * → `payload`), уже корректные данные не меняются.
     */
    private fun normalizeSessionJson(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        return normalizePayload(normalizeLog(obj))
    }

    /**
     * Превращает старое поле `generated: [Int]` в новый `log: [{value, at}]`.
     *
     * Время в таких записях неизвестно, поэтому проставляется `0`.
     */
    private fun normalizeLog(obj: JsonObject): JsonObject {
        if (LOG in obj) return obj
        val legacy = obj[LEGACY_GENERATED] as? JsonArray ?: return obj
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

    /**
     * Собирает payload сессии из старого плоского формата.
     *
     * Старый формат: `min`, `max`, `allowRepeats` лежат в корне объекта
     * сессии, поля `payload` нет. Текущий: объект с `type: "numbers"`
     * внутри `payload`. Промежуточный вид (после [normalizeLog]) содержит
     * и корневые поля, и `log` — он тоже читается.
     */
    private fun normalizePayload(obj: JsonObject): JsonObject {
        if (PAYLOAD in obj) return obj

        val min = obj.intField("min") ?: return legacyNumbersFallback(obj)
        val max = obj.intField("max") ?: return legacyNumbersFallback(obj)
        val allowRepeats = (obj["allowRepeats"] as? JsonPrimitive)?.contentOrNull
            ?.toBooleanStrictOrNull() ?: return legacyNumbersFallback(obj)

        val payload = buildJsonObject {
            put(TYPE, TYPE_NUMBERS)
            put("min", JsonPrimitive(min))
            put("max", JsonPrimitive(max))
            put("allowRepeats", JsonPrimitive(allowRepeats))
        }
        val mutable = obj.toMutableMap()
        mutable[PAYLOAD] = payload
        // Корневые min/max/allowRepeats больше не поля Session: убираем,
        // иначе декодер упадёт на неизвестном поле.
        mutable.remove("min")
        mutable.remove("max")
        mutable.remove("allowRepeats")
        return JsonObject(mutable)
    }

    /**
     * Плашка для записи без корневых полей диапазона: режим чисел
     * с некорректным (не представимым) диапазоном.
     *
     * Такой объект в норме не встречается (в корне всегда были min/max),
     * но данные могли быть обрезаны. Декодер не должен ронять весь список:
     * сессия читается с заведомо пустым диапазоном `1..1`, видимое
     * отличие помогает заметить проблему.
     */
    private fun legacyNumbersFallback(obj: JsonObject): JsonObject {
        val payload = buildJsonObject {
            put(TYPE, TYPE_NUMBERS)
            put("min", JsonPrimitive(1))
            put("max", JsonPrimitive(1))
            put("allowRepeats", JsonPrimitive(true))
        }
        return JsonObject(obj.toMutableMap().apply { put(PAYLOAD, payload) })
    }

    /** Целочисленное поле корня объекта (или `null`). */
    private fun JsonObject.intField(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull

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
