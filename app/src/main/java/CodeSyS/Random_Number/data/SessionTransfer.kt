package CodeSyS.Random_Number.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Экспорт/импорт сессий в переносимом виде.
 *
 * Формат — тот же JSON, что и у хранилища, но в отдельной обёртке
 * с версией: это позволяет отличать файл сессий от других JSON
 * и корректно сообщать о несовместимой версии.
 *
 * Обратная совместимость: файлы, экспортированные до появления режимов,
 * содержат сессии без `payload` (поля диапазона в корне) — они читаются
 * через ту же нормализацию, что и хранилище ([SessionCodec]).
 */
object SessionTransfer {

    /** Текущая версия формата. */
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        classDiscriminator = "type"
    }

    /**
     * Файл экспорта.
     *
     * @param exportedAt время выгрузки (epoch millis) — для ориентира
     * при импорте с другого устройства.
     */
    @Serializable
    data class Bundle(
        val version: Int = VERSION,
        val exportedAt: Long = 0L,
        val sessions: List<Session> = emptyList(),
    )

    /** Сериализует [sessions] в файл экспорта. */
    fun export(sessions: List<Session>, exportedAt: Long): String = json.encodeToString(
        Bundle(
            exportedAt = exportedAt,
            sessions = sessions.sortedByDescending { it.lastUsedAt },
        ),
    )

    /** Результат разбора файла экспорта. */
    sealed interface ImportResult {
        /** Файл разобран; [imported] — сколько сессий принято. */
        data class Success(val imported: Int) : ImportResult

        /** Файл пуст или повреждён. */
        data class Corrupted(val raw: String) : ImportResult

        /** Версия файла новее, чем поддерживает приложение. */
        data class UnsupportedVersion(val version: Int) : ImportResult
    }

    /**
     * Разбирает файл экспорта.
     *
     * Некорректный файл не считается пустым импортом: [ImportResult.Corrupted]
     * позволяет показать пользователю ошибку, а не «съесть» данные.
     */
    fun parse(raw: String): ImportResult {
        if (raw.isBlank()) return ImportResult.Corrupted(raw)
        val bundle = runCatching { decodeBundle(raw) }
            .getOrElse { return ImportResult.Corrupted(raw) }
        if (bundle.version > VERSION) return ImportResult.UnsupportedVersion(bundle.version)
        if (bundle.sessions.isEmpty()) return ImportResult.Corrupted(raw)
        return ImportResult.Success(bundle.sessions.size)
    }

    /**
     * Извлекает сессии из файла экспорта.
     *
     * @return сессии или `null`, если файл не разбирается/версия
     * не поддерживается.
     */
    fun read(raw: String): List<Session>? {
        if (raw.isBlank()) return null
        val bundle = runCatching { decodeBundle(raw) }.getOrNull() ?: return null
        if (bundle.version > VERSION || bundle.sessions.isEmpty()) return null
        return bundle.sessions
    }

    /**
     * Разбирает bundle, нормализуя каждую сессию:
     * старый плоский формат (без `payload`) читается через [SessionCodec]
     * — та же нормализация, что и в хранилище.
     */
    private fun decodeBundle(raw: String): Bundle {
        val root = json.parseToJsonElement(raw).jsonObject
        val sessions = root["sessions"]?.jsonArray
            ?.mapNotNull { entry ->
                (entry as? JsonObject)
                    ?.let { SessionCodec.decodeOne(it.toString()) }
            }
            ?: emptyList()
        return Bundle(
            version = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: VERSION,
            exportedAt = (root["exportedAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
            sessions = sessions,
        )
    }
}
