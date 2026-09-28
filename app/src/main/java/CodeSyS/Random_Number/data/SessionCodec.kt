package CodeSyS.Random_Number.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON-кодек списка сессий для хранилища.
 *
 * Чистая (не-Android) логика — покрывается unit-тестами.
 *
 * Вычисляемые свойства [Session] (`rangeSize`, `isExhausted`…) в JSON
 * не попадают — сериализуются только свойства конструктора.
 */
object SessionCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Сериализует список сессий в JSON-строку для хранилища. */
    fun encode(sessions: List<Session>): String = json.encodeToString(sessions)

    /**
     * Декодирует список сессий.
     *
     * @return пустой список, если [raw] пуст или данные повреждены
     * (повреждённое хранилище не должно ронять приложение).
     */
    fun decode(raw: String?): List<Session> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Session>>(raw) }
            .getOrDefault(emptyList())
    }
}
