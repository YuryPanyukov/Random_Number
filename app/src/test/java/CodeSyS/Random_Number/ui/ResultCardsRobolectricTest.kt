package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.domain.CoinSide
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke-тесты композаблов результата на Robolectric (без эмулятора):
 * карточки монеты и кубиков отображают итоговое значение после рендера.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultCardsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun coinCardShowsSideLetter() {
        composeRule.setContent {
            RandomNumbersTheme {
                CoinResultCard(
                    sides = listOf(CoinSide.HEADS),
                    nonce = 0,
                    sideLabel = { it.label },
                    onCopy = {},
                )
            }
        }
        // «О» — первая буква локализованной стороны монеты.
        composeRule.onNodeWithText("О").assertExists()
    }

    @Test
    fun diceWithMoreThanPipLimitShowsDigitsAndSum() {
        composeRule.setContent {
            RandomNumbersTheme {
                DiceResultCard(
                    values = listOf(7, 13),
                    diceFaces = 20,
                    nonce = 0,
                    onCopy = {},
                )
            }
        }
        // Сумма броска (7 + 13) показывается крупным шрифтом.
        composeRule.onNodeWithText("20").assertExists()
    }
}
