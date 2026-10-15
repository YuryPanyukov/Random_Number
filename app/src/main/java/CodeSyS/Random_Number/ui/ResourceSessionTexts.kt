package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.data.DefaultSessionTexts
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.data.SessionTexts
import CodeSyS.Random_Number.domain.CoinSide
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Локализованные [SessionTexts] из ресурсов приложения.
 *
 * Заголовки сессий вычисляются из payload при показе, поэтому смена языка
 * сразу отражается на всех сессиях, включая созданные ранее.
 */
class ResourceSessionTexts(
    private val resources: Resources,
) : SessionTexts {

    override fun title(session: Session): String = when (val payload = session.payload) {
        is SessionPayload.Numbers ->
            if (session.seedFromSecure) {
                resources.getString(R.string.title_honest_draw)
            } else {
                numbersBase(payload) + repeatsSuffix(payload.allowRepeats)
            }

        is SessionPayload.Items ->
            if (session.seedFromSecure) {
                resources.getString(R.string.title_honest_draw_list)
            } else {
                resources.getString(R.string.title_list, payload.items.size) +
                    repeatsSuffix(payload.allowRepeats)
            }

        is SessionPayload.Dice -> payload.label
        is SessionPayload.Coin -> resources.getString(R.string.title_coin)
        is SessionPayload.Shuffle ->
            resources.getString(R.string.title_shuffle, payload.items.size)
    }

    override fun coinSide(side: CoinSide): String = resources.getString(
        when (side) {
            CoinSide.HEADS -> R.string.coin_heads
            CoinSide.TAILS -> R.string.coin_tails
        },
    )

    override fun rangeLabel(session: Session): String = when (val payload = session.payload) {
        is SessionPayload.Items -> resources.getString(R.string.export_range_items, payload.items.size)
        is SessionPayload.Dice -> payload.label
        is SessionPayload.Coin -> resources.getString(R.string.export_range_coin)
        is SessionPayload.Shuffle ->
            resources.getString(R.string.export_range_items, payload.items.size)

        is SessionPayload.Numbers -> numbersBase(payload)
    }

    override val markdownValueHeader: String
        get() = resources.getString(R.string.export_markdown_value)

    override val markdownTimeHeader: String
        get() = resources.getString(R.string.export_markdown_time)

    override val unknownTime: String
        get() = resources.getString(R.string.export_unknown_time)

    override val manualSeedSource: String
        get() = resources.getString(R.string.share_manual_seed_source)

    override val shareVerificationHint: String
        get() = resources.getString(R.string.share_verification_hint)

    /** Базовая подпись диапазона чисел: `min..max`. */
    private fun numbersBase(payload: SessionPayload.Numbers): String =
        resources.getString(R.string.export_range_numbers, payload.min, payload.max)

    /** Суффикс режима «без повторов» (пустой, если повторы разрешены). */
    private fun repeatsSuffix(allowRepeats: Boolean): String =
        if (allowRepeats) "" else resources.getString(R.string.title_without_repeats_suffix)
}

/**
 * Строки текущей локали для композаблов. Значение по умолчанию — русские
 * строки ([DefaultSessionTexts]): preview и тесты не требуют провайдера.
 */
val LocalSessionTexts = staticCompositionLocalOf<SessionTexts> { DefaultSessionTexts }

/** Создаёт [SessionTexts] из ресурсов текущего контекста (для провайдера). */
@Composable
fun rememberSessionTexts(): SessionTexts {
    val resources = LocalContext.current.resources
    return remember(resources) { ResourceSessionTexts(resources) }
}
