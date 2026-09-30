package CodeSyS.Random_Number.ui.theme

import CodeSyS.Random_Number.data.AccentColor
import CodeSyS.Random_Number.data.ThemeMode
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    secondary = IndigoGrey80,
    tertiary = Amber80,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    secondary = IndigoGrey40,
    tertiary = Amber40,
)

/** Основной цвет палитры для выбранного акцента. */
private fun primaryFor(accent: AccentColor, dark: Boolean): androidx.compose.ui.graphics.Color =
    when (accent) {
        AccentColor.DYNAMIC, AccentColor.INDIGO -> if (dark) Indigo80 else Indigo40
        AccentColor.TEAL -> if (dark) Teal80 else Teal40
        AccentColor.ROSE -> if (dark) Rose80 else Rose40
    }

/**
 * Тема приложения «Случайные числа».
 *
 * Индиго-палитра с золотым акцентом; на Android 12+ при акценте
 * [AccentColor.DYNAMIC] используется динамическая тема системы.
 *
 * @param themeMode режим темы: следовать системе / светлая / тёмная.
 * @param accent выбираемый акцентный цвет.
 */
@Composable
fun RandomNumbersTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = primaryFor(accent, dark = true),
            secondary = IndigoGrey80,
            tertiary = Amber80,
        )

        else -> lightColorScheme(
            primary = primaryFor(accent, dark = false),
            secondary = IndigoGrey40,
            tertiary = Amber40,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
