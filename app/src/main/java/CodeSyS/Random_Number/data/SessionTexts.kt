package CodeSyS.Random_Number.data

import CodeSyS.Random_Number.domain.CoinSide

/**
 * Пользовательские строки, зависящие от локали: заголовки сессий, названия
 * сторон монеты, служебные подписи шаринга и экспорта.
 *
 * Вынесены в интерфейс, чтобы `data`/`domain` оставались без Android:
 * приложение использует реализацию на ресурсах
 * ([CodeSyS.Random_Number.ui.ResourceSessionTexts]), а тесты и значения
 * по умолчанию — [DefaultSessionTexts] (русские строки).
 *
 * Заголовок сессии вычисляется здесь из [Session.payload], а не берётся
 * из [Session.title]: это позволяет показывать заголовки на текущем языке
 * даже для сессий, созданных в другой локали.
 */
interface SessionTexts {

    /** Заголовок сессии по её содержимому (в текущей локали). */
    fun title(session: Session): String

    /** Название стороны монеты. */
    fun coinSide(side: CoinSide): String

    /** Описание множества значений сессии (диапазон, список, бросок, монета). */
    fun rangeLabel(session: Session): String

    /** Заголовок колонки значений в Markdown-таблице. */
    val markdownValueHeader: String

    /** Заголовок колонки времени в Markdown-таблице. */
    val markdownTimeHeader: String

    /** Маркер записи без известного времени. */
    val unknownTime: String

    /** Источник seed, зафиксированного вручную (в шаринге результата). */
    val manualSeedSource: String

    /** Подсказка о воспроизводимости при шаринге сессии с seed. */
    val shareVerificationHint: String
}

/**
 * Русская реализация [SessionTexts] по умолчанию (как было до локализации).
 *
 * Используется в тестах, а также как безопасный фолбэк там, где ресурсы
 * недоступны.
 */
object DefaultSessionTexts : SessionTexts {

    override fun title(session: Session): String = when (val payload = session.payload) {
        is SessionPayload.Numbers ->
            if (session.seedFromSecure) {
                "Честный розыгрыш"
            } else {
                numbersBase(payload) + repeatsSuffix(payload.allowRepeats)
            }

        is SessionPayload.Items ->
            if (session.seedFromSecure) {
                "Честный розыгрыш (список)"
            } else {
                "Список (${payload.items.size})" + repeatsSuffix(payload.allowRepeats)
            }

        is SessionPayload.Dice -> payload.label
        is SessionPayload.Coin -> "Монета"
        is SessionPayload.Shuffle -> "Перемешивание (${payload.items.size})"
    }

    override fun coinSide(side: CoinSide): String = side.label

    override fun rangeLabel(session: Session): String = when (val payload = session.payload) {
        is SessionPayload.Items -> "${payload.items.size} элементов"
        is SessionPayload.Dice -> payload.label
        is SessionPayload.Coin -> "Орёл/Решка"
        is SessionPayload.Shuffle -> "${payload.items.size} элементов"
        is SessionPayload.Numbers -> numbersBase(payload)
    }

    override val markdownValueHeader: String = "Значение"
    override val markdownTimeHeader: String = "Время"
    override val unknownTime: String = "—"
    override val manualSeedSource: String = "задан вручную"
    override val shareVerificationHint: String = "Проверка: с этим seed генерация повторяется."

    /** Базовая подпись диапазона чисел: `min..max`. */
    private fun numbersBase(payload: SessionPayload.Numbers): String =
        "${payload.min}..${payload.max}"

    /** Суффикс режима «без повторов» (пустой, если повторы разрешены). */
    private fun repeatsSuffix(allowRepeats: Boolean): String =
        if (allowRepeats) "" else ", без повторов"
}
