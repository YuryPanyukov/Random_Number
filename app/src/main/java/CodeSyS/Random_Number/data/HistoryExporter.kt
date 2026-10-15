package CodeSyS.Random_Number.data

import java.util.Calendar

/**
 * Экспорт истории сессии в текстовые форматы (CSV/TXT/JSON/Markdown).
 *
 * Чистые функции без Android — покрываются unit-тестами. Пользовательские
 * строки (заголовок, описание, колонки Markdown, стороны монеты) берутся
 * из [SessionTexts]; по умолчанию — [DefaultSessionTexts] (русские).
 */
object HistoryExporter {

    /** Экспортируемые форматы. */
    enum class Format(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        TXT("txt", "text/plain"),
        JSON("json", "application/json"),
        MARKDOWN("md", "text/markdown"),
    }

    /**
     * Разделитель по умолчанию: точка с запятой — так Excel (в русской
     * локали) открывает CSV без мастера импорта.
     */
    private const val CSV_DELIMITER = ';'

    private const val CSV_HEADER = "value;at"

    /** Текстовый формат: заголовок, описание и значения по одному в строке. */
    fun toTxt(session: Session, texts: SessionTexts = DefaultSessionTexts): String = buildString {
        appendLine(texts.title(session))
        appendLine(texts.rangeLabel(session))
        session.generatedDisplay(texts).forEach { appendLine(it) }
    }.trimEnd()

    /**
     * CSV: значение и время выдачи (epoch millis).
     *
     * Заголовок всегда есть, чтобы файл самодостаточен; `at = 0`
     * означает «время неизвестно» (данные, сохранённые до журнала).
     * В режиме списка в колонке `value` — сам элемент, при необходимости
     * экранированный кавычками.
     */
    fun toCsv(session: Session, texts: SessionTexts = DefaultSessionTexts): String = buildString {
        appendLine(CSV_HEADER)
        session.log.forEach { entry ->
            appendLine("${escapeCsv(session.displayOf(entry.value, texts))}$CSV_DELIMITER${entry.at}")
        }
    }.trimEnd()

    /**
     * Markdown: заголовок, описание множества значений и таблица
     * «номер | значение | время».
     *
     * Время — человекочитаемое (локальная зона), [SessionTexts.unknownTime]
     * для записей без времени; удобно вставлять в заметки и чаты.
     */
    fun toMarkdown(session: Session, texts: SessionTexts = DefaultSessionTexts): String = buildString {
        appendLine("# ${texts.title(session)}")
        appendLine()
        appendLine(texts.rangeLabel(session))
        appendLine()
        appendLine("| # | ${texts.markdownValueHeader} | ${texts.markdownTimeHeader} |")
        appendLine("| --- | --- | --- |")
        session.log.forEachIndexed { index, entry ->
            appendLine(
                "| ${index + 1} | ${escapeMarkdown(session.displayOf(entry.value, texts))} | " +
                    "${formatDateTime(entry.at, texts)} |",
            )
        }
    }.trimEnd()

    /** Экранирует значение CSV, если оно содержит разделитель, кавычку или перевод строки. */
    private fun escapeCsv(value: String): String =
        if (value.any { it == CSV_DELIMITER || it == '\"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Экранирует значение Markdown: вертикальная черта ломает таблицу. */
    private fun escapeMarkdown(value: String): String =
        value.replace("|", "\\|").replace("\n", " ").replace("\r", "")

    /**
     * Человекочитаемое время (локальная зона), например `2026-10-05 12:30`.
     *
     * `at <= 0` означает «время неизвестно» (записи до появления журнала) —
     * для них выводится [SessionTexts.unknownTime].
     */
    private fun formatDateTime(epochMillis: Long, texts: SessionTexts): String {
        if (epochMillis <= 0L) return texts.unknownTime
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return "%04d-%02d-%02d %02d:%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }

    /** JSON-выгрузка одной сессии целиком (с диапазоном и журналом). */
    fun toJson(session: Session): String = SessionCodec.encodeOne(session)

    /** Экспортирует [session] в выбранный формат. */
    fun export(
        session: Session,
        format: Format,
        texts: SessionTexts = DefaultSessionTexts,
    ): String = when (format) {
        Format.CSV -> toCsv(session, texts)
        Format.TXT -> toTxt(session, texts)
        Format.JSON -> toJson(session)
        Format.MARKDOWN -> toMarkdown(session, texts)
    }

    /** Имя файла для «Сохранить как…»: `session-2026-09-29.csv`. */
    fun fileName(format: Format, now: Long): String =
        "session-${formatDate(now)}.${format.extension}"

    /** Формат даты для имени файла (без локали и разделителей). */
    private fun formatDate(epochMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = epochMillis
        return "%04d%02d%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }
}
