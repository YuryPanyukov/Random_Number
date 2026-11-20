package CodeSyS.Random_Number.ui.theme

import CodeSyS.Random_Number.data.AccentColor
import CodeSyS.Random_Number.data.ThemeMode
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Скругления Material: крупные карточки и мягкие кнопки. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = NightPrimaryContainer,
    onPrimaryContainer = NightOnPrimaryContainer,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    secondaryContainer = NightSecondaryContainer,
    onSecondaryContainer = NightOnSecondaryContainer,
    tertiary = NightTertiary,
    onTertiary = NightOnTertiary,
    tertiaryContainer = NightTertiaryContainer,
    onTertiaryContainer = NightOnTertiaryContainer,
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMuted,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    error = NightError,
    onError = NightOnError,
)

private val LightColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = DayOnPrimary,
    primaryContainer = DayPrimaryContainer,
    onPrimaryContainer = DayOnPrimaryContainer,
    secondary = DaySecondary,
    onSecondary = DayOnSecondary,
    secondaryContainer = DaySecondaryContainer,
    onSecondaryContainer = DayOnSecondaryContainer,
    tertiary = DayTertiary,
    onTertiary = DayOnTertiary,
    tertiaryContainer = DayTertiaryContainer,
    onTertiaryContainer = DayOnTertiaryContainer,
    background = DayBackground,
    onBackground = DayOnSurface,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayMuted,
    outline = DayOutline,
    outlineVariant = DayOutlineVariant,
    error = DayError,
    onError = DayOnError,
)

/** Основной цвет, его контейнер и контент для выбранного акцента. */
private fun ColorScheme.withAccent(accent: AccentColor, dark: Boolean): ColorScheme = when (accent) {
    AccentColor.TEAL -> copy(
        primary = if (dark) TealNight else TealDay,
        onPrimary = if (dark) TealNightOn else TealDayOn,
        primaryContainer = if (dark) TealNightContainer else TealDayContainer,
        onPrimaryContainer = if (dark) TealNightOnContainer else TealDayOnContainer,
    )

    AccentColor.ROSE -> copy(
        primary = if (dark) RoseNight else RoseDay,
        onPrimary = if (dark) RoseNightOn else RoseDayOn,
        primaryContainer = if (dark) RoseNightContainer else RoseDayContainer,
        onPrimaryContainer = if (dark) RoseNightOnContainer else RoseDayOnContainer,
    )

    // INDIGO и DYNAMIC без динамической темы используют фирменный синий.
    AccentColor.INDIGO, AccentColor.DYNAMIC -> this
}

/**
 * Тема приложения «Случайные числа».
 *
 * Тёмная палитра с глубоким фоном и тонкими обводками; на Android 12+
 * при акценте [AccentColor.DYNAMIC] используется динамическая тема системы.
 *
 * @param themeMode режим темы: следовать системе / светлая / тёмная.
 * @param accent выбираемый акцентный цвет.
 */
@Composable
fun RandomNumbersTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.INDIGO,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = when {
        accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> base.withAccent(accent, dark = darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}

/** Цвет фона карточки: приподнятая поверхность палитры. */
val ColorScheme.cardSurface: Color
    get() = surface
