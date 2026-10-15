package CodeSyS.Random_Number.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExporterTest {

    private val session = Session(
        id = "s1",
        title = "1..10, без повторов",
        min = 1,
        max = 10,
        allowRepeats = false,
    ).withGenerated(listOf(3, 7), at = 1_000L)
        .withGenerated(listOf(1), at = 2_000L)

    @Test
    fun `csv - contains header and every value with timestamp`() {
        val csv = HistoryExporter.toCsv(session)

        val lines = csv.lines()
        assertEquals("value;at", lines.first())
        assertEquals("3;1000", lines[1])
        assertEquals("7;1000", lines[2])
        assertEquals("1;2000", lines[3])
    }

    @Test
    fun `csv - empty history still has header`() {
        val empty = session.withClearedNumbers(at = 1L)

        assertEquals("value;at", HistoryExporter.toCsv(empty))
    }

    @Test
    fun `txt - title, range and one number per line`() {
        val txt = HistoryExporter.toTxt(session)

        val lines = txt.lines()
        assertEquals("1..10, без повторов", lines[0])
        assertEquals("1..10", lines[1])
        assertEquals(listOf("3", "7", "1"), lines.drop(2))
    }

    @Test
    fun `json - round trips through the codec`() {
        val json = HistoryExporter.toJson(session)

        assertEquals(session, SessionCodec.decodeOne(json))
    }

    @Test
    fun `export - dispatches by format`() {
        assertEquals(
            HistoryExporter.toCsv(session),
            HistoryExporter.export(session, HistoryExporter.Format.CSV),
        )
        assertEquals(
            HistoryExporter.toTxt(session),
            HistoryExporter.export(session, HistoryExporter.Format.TXT),
        )
        assertEquals(
            HistoryExporter.toJson(session),
            HistoryExporter.export(session, HistoryExporter.Format.JSON),
        )
        assertEquals(
            HistoryExporter.toMarkdown(session),
            HistoryExporter.export(session, HistoryExporter.Format.MARKDOWN),
        )
    }

    @Test
    fun `markdown - title, table header and one row per value`() {
        val lines = HistoryExporter.toMarkdown(session).lines()

        assertEquals("# 1..10, без повторов", lines[0])
        assertEquals("1..10", lines[2])
        assertEquals("| # | Значение | Время |", lines[4])
        assertEquals("| --- | --- | --- |", lines[5])
        // Три записи журнала → три строки таблицы (время зависит от зоны,
        // поэтому проверяем только начало и конец строки).
        assertEquals(3, lines.size - 6)
        assertTrue(lines[6].startsWith("| 1 | 3 | "))
        assertTrue(lines[6].endsWith(" |"))
        assertTrue(lines[7].startsWith("| 2 | 7 | "))
        assertTrue(lines[8].startsWith("| 3 | 1 | "))
    }

    @Test
    fun `markdown - empty history has header but no rows`() {
        val empty = session.withClearedNumbers(at = 1L)

        assertEquals(6, HistoryExporter.toMarkdown(empty).lines().size)
    }

    @Test
    fun `markdown - unknown time is shown as a dash`() {
        val legacy = session.copy(log = listOf(GeneratedEntry(5, 0L)))

        assertTrue(HistoryExporter.toMarkdown(legacy).contains("| 1 | 5 | — |"))
    }

    @Test
    fun `markdown - pipes in values are escaped`() {
        val tricky = Session(
            id = "m1",
            title = "t",
            payload = SessionPayload.Items(items = listOf("a|b"), allowRepeats = true),
        ).withGenerated(listOf(0), at = 1L)

        assertTrue(HistoryExporter.toMarkdown(tricky).contains("| 1 | a\\|b |"))
    }

    @Test
    fun `file name - has date and format extension`() {
        val name = HistoryExporter.fileName(HistoryExporter.Format.CSV, now = 1_757_000_000_000L)

        assertTrue(name.startsWith("session-"))
        assertTrue(name.endsWith(".csv"))
        // Дата в имени — 8 цифр: YYYYMMDD
        assertTrue(name.removePrefix("session-").removeSuffix(".csv").all { it.isDigit() })
    }

    @Test
    fun `format - mime types are set`() {
        assertEquals("text/csv", HistoryExporter.Format.CSV.mimeType)
        assertEquals("text/plain", HistoryExporter.Format.TXT.mimeType)
        assertEquals("application/json", HistoryExporter.Format.JSON.mimeType)
        assertEquals("text/markdown", HistoryExporter.Format.MARKDOWN.mimeType)
    }

    // --- Режим «элемент из списка» (2.1) ---

    private val itemsSession = Session(
        id = "i1",
        title = "Список (3)",
        payload = SessionPayload.Items(
            items = listOf("Аня", "Борис", "Вера"),
            allowRepeats = false,
        ),
        createdAt = 1L,
        lastUsedAt = 2L,
    ).withGenerated(listOf(2, 0), at = 1_000L)

    @Test
    fun `txt items - title, count and item names`() {
        val lines = HistoryExporter.toTxt(itemsSession).lines()

        // Заголовок вычисляется из payload: список «без повторов».
        assertEquals("Список (3), без повторов", lines[0])
        assertEquals("3 элементов", lines[1])
        assertEquals(listOf("Вера", "Аня"), lines.drop(2))
    }

    @Test
    fun `csv items - values are item names`() {
        val lines = HistoryExporter.toCsv(itemsSession).lines()

        assertEquals("value;at", lines[0])
        assertEquals("Вера;1000", lines[1])
        assertEquals("Аня;1000", lines[2])
    }

    @Test
    fun `csv items - delimiter and quotes are escaped`() {
        val tricky = Session(
            id = "i2",
            title = "t",
            payload = SessionPayload.Items(
                items = listOf("a;b", "say \"hi\""),
                allowRepeats = true,
            ),
        ).withGenerated(listOf(0, 1), at = 5L)

        val lines = HistoryExporter.toCsv(tricky).lines()

        assertEquals("\"a;b\";5", lines[1])
        assertEquals("\"say \"\"hi\"\"\";5", lines[2])
    }

    // --- Режим «кубики» (2.2) ---

    private val diceSession = Session(
        id = "d1",
        title = "2d6",
        payload = SessionPayload.Dice(count = 2, sides = 6),
        createdAt = 1L,
        lastUsedAt = 2L,
    ).withGenerated(listOf(4, 2), at = 1_000L)

    @Test
    fun `txt dice - title, notation and die values`() {
        val lines = HistoryExporter.toTxt(diceSession).lines()

        assertEquals("2d6", lines[0])
        assertEquals("2d6", lines[1])
        assertEquals(listOf("4", "2"), lines.drop(2))
    }

    @Test
    fun `csv dice - values are die numbers with timestamps`() {
        val lines = HistoryExporter.toCsv(diceSession).lines()

        assertEquals("value;at", lines[0])
        assertEquals("4;1000", lines[1])
        assertEquals("2;1000", lines[2])
    }

    // --- Режим «монета» (2.3) ---

    private val coinSession = Session(
        id = "c1",
        title = "Монета",
        payload = SessionPayload.Coin,
        createdAt = 1L,
        lastUsedAt = 2L,
    ).withGenerated(listOf(0, 1, 0), at = 1_000L)

    @Test
    fun `txt coin - title, side header and outcome names`() {
        val lines = HistoryExporter.toTxt(coinSession).lines()

        assertEquals("Монета", lines[0])
        assertEquals("Орёл/Решка", lines[1])
        assertEquals(listOf("Орёл", "Решка", "Орёл"), lines.drop(2))
    }

    @Test
    fun `csv coin - values are side names`() {
        val lines = HistoryExporter.toCsv(coinSession).lines()

        assertEquals("Орёл;1000", lines[1])
        assertEquals("Решка;1000", lines[2])
    }

    // --- Режим «перемешивание» (2.4) ---

    private val shuffleSession = Session(
        id = "sh1",
        title = "Перемешивание (3)",
        payload = SessionPayload.Shuffle(items = listOf("Аня", "Борис", "Вера")),
        createdAt = 1L,
        lastUsedAt = 2L,
    ).withGenerated(listOf(2, 0, 1), at = 1_000L)

    @Test
    fun `txt shuffle - title, count and shuffled order`() {
        val lines = HistoryExporter.toTxt(shuffleSession).lines()

        assertEquals("Перемешивание (3)", lines[0])
        assertEquals("3 элементов", lines[1])
        assertEquals(listOf("Вера", "Аня", "Борис"), lines.drop(2))
    }

    @Test
    fun `csv shuffle - values are item names`() {
        val lines = HistoryExporter.toCsv(shuffleSession).lines()

        assertEquals("value;at", lines[0])
        assertEquals("Вера;1000", lines[1])
        assertEquals("Аня;1000", lines[2])
        assertEquals("Борис;1000", lines[3])
    }

    // --- Локализация (SessionTexts) ---

    @Test
    fun `txt - title and range come from provided texts`() {
        val lines = HistoryExporter.toTxt(session, FakeSessionTexts()).lines()

        assertEquals("TITLE", lines[0])
        assertEquals("RANGE", lines[1])
    }

    @Test
    fun `csv coin - values use provided coin labels`() {
        val lines = HistoryExporter.toCsv(coinSession, FakeSessionTexts()).lines()

        assertEquals("HEADS_EN;1000", lines[1])
        assertEquals("TAILS_EN;1000", lines[2])
    }

    @Test
    fun `markdown - headers and unknown time come from provided texts`() {
        val legacy = session.copy(log = listOf(GeneratedEntry(1, 0L)))

        val lines = HistoryExporter.toMarkdown(legacy, FakeSessionTexts()).lines()

        assertEquals("# TITLE", lines[0])
        assertEquals("RANGE", lines[2])
        assertEquals("| # | VALUE | TIME |", lines[4])
        assertTrue(lines[6].endsWith("| NONE |"))
    }
}

class SessionTransferTest {

    private val sessions = listOf(
        Session.new(min = 1, max = 10, allowRepeats = false, now = 100L),
        Session.new(min = 1, max = 6, allowRepeats = true, now = 200L)
            .withGenerated(listOf(4), at = 300L),
    )

    @Test
    fun `export then parse - all sessions survive`() {
        val raw = SessionTransfer.export(sessions, exportedAt = 500L)

        val result = SessionTransfer.parse(raw)

        assertTrue(result is SessionTransfer.ImportResult.Success)
        assertEquals(2, (result as SessionTransfer.ImportResult.Success).imported)
        assertEquals(sessions.size, SessionTransfer.read(raw)!!.size)
    }

    @Test
    fun `export - sessions sorted by lastUsedAt descending`() {
        val raw = SessionTransfer.export(sessions, exportedAt = 1L)

        val read = SessionTransfer.read(raw)!!

        assertEquals(300L, read.first().lastUsedAt)
        assertEquals(100L, read.last().lastUsedAt)
    }

    @Test
    fun `export - history and timestamps preserved`() {
        val raw = SessionTransfer.export(listOf(sessions[1]), exportedAt = 1L)

        val restored = SessionTransfer.read(raw)!!.single()

        assertEquals(listOf(4), restored.generated)
        assertEquals(300L, restored.log.single().at)
    }

    @Test
    fun `parse - empty and broken files report Corrupted`() {
        assertTrue(SessionTransfer.parse("") is SessionTransfer.ImportResult.Corrupted)
        assertTrue(SessionTransfer.parse("{не json") is SessionTransfer.ImportResult.Corrupted)
        val missingVersion = """{"version":1,"sessions":[]}"""
        assertTrue(SessionTransfer.parse(missingVersion) is SessionTransfer.ImportResult.Corrupted)
    }

    @Test
    fun `parse - newer version is reported instead of silently dropped`() {
        val raw = """{"version":999,"exportedAt":0,"sessions":[]}"""

        val result = SessionTransfer.parse(raw)

        assertTrue(result is SessionTransfer.ImportResult.UnsupportedVersion)
        assertEquals(999, (result as SessionTransfer.ImportResult.UnsupportedVersion).version)
        assertNull(SessionTransfer.read(raw))
    }

    @Test
    fun `read - returns null for broken data`() {
        assertNull(SessionTransfer.read("{не json"))
        assertNull(SessionTransfer.read(""))
    }

    @Test
    fun `export - version and timestamp are recorded`() {
        val raw = SessionTransfer.export(sessions, exportedAt = 777L)

        assertTrue(raw.contains("\"version\": ${SessionTransfer.VERSION}"))
        assertTrue(raw.contains("\"exportedAt\": 777"))
    }

    @Test
    fun `read - legacy bundle without payload is migrated`() {
        // Файл, экспортированный до появления режимов: поля диапазона
        // в корне сессии, журнал — плоским списком.
        val legacyBundle = """
            {"version":1,"exportedAt":500,"sessions":[
              {"id":"l1","title":"t","min":1,"max":45,"allowRepeats":false,
               "generated":[4,11],"createdAt":100,"lastUsedAt":300}
            ]}
        """.trimIndent()

        val restored = SessionTransfer.read(legacyBundle)!!.single()

        assertEquals("l1", restored.id)
        assertEquals(SessionPayload.Numbers(min = 1, max = 45, allowRepeats = false), restored.payload)
        assertEquals(listOf(4, 11), restored.generated)
    }
}
