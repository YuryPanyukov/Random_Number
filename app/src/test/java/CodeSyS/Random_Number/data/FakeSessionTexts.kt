package CodeSyS.Random_Number.data

import CodeSyS.Random_Number.domain.CoinSide

/**
 * Тестовая реализация [SessionTexts] с узнаваемыми строками: позволяет
 * проверить, что заголовки, метки сторон монеты и служебные подписи
 * действительно берутся из переданного резолвера, а не из значений
 * по умолчанию.
 */
class FakeSessionTexts : SessionTexts {

    override fun title(session: Session): String = "TITLE"

    override fun coinSide(side: CoinSide): String = when (side) {
        CoinSide.HEADS -> "HEADS_EN"
        CoinSide.TAILS -> "TAILS_EN"
    }

    override fun rangeLabel(session: Session): String = "RANGE"

    override val markdownValueHeader: String = "VALUE"
    override val markdownTimeHeader: String = "TIME"
    override val unknownTime: String = "NONE"
    override val manualSeedSource: String = "MANUAL"
    override val shareVerificationHint: String = "VERIFY"
}
