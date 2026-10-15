package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.data.AccentColor
import CodeSyS.Random_Number.data.AppSettings
import CodeSyS.Random_Number.data.ThemeMode
import CodeSyS.Random_Number.ui.settings.SettingsViewModel
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Экран настроек: режим темы, акцентный цвет, вибрация и звук.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory),
) {
    val settings by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            settings = settings,
            contentPadding = innerPadding,
            onThemeMode = viewModel::setThemeMode,
            onAccent = viewModel::setAccent,
            onVibration = viewModel::setVibrationEnabled,
            onSound = viewModel::setSoundEnabled,
            onFeedbackPerNumber = viewModel::setFeedbackPerNumber,
        )
    }
}

/** Содержимое настроек (отдельно от обёртки ради Preview). */
@Composable
private fun SettingsContent(
    settings: AppSettings,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onVibration: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onFeedbackPerNumber: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle(stringResource(R.string.theme))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.accent_color))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentColor.entries.forEach { accent ->
                FilterChip(
                    selected = settings.accent == accent,
                    onClick = { onAccent(accent) },
                    label = {
                        Text(
                            stringResource(
                                when (accent) {
                                    AccentColor.DYNAMIC -> R.string.accent_dynamic
                                    AccentColor.INDIGO -> R.string.accent_indigo
                                    AccentColor.TEAL -> R.string.accent_teal
                                    AccentColor.ROSE -> R.string.accent_rose
                                },
                            ),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        SectionTitle(stringResource(R.string.feedback_section))

        ToggleRow(
            title = stringResource(R.string.vibration),
            hint = stringResource(R.string.vibration_hint),
            checked = settings.vibrationEnabled,
            onCheckedChange = onVibration,
        )
        ToggleRow(
            title = stringResource(R.string.sound),
            hint = stringResource(R.string.sound_hint),
            checked = settings.soundEnabled,
            onCheckedChange = onSound,
        )
        ToggleRow(
            title = stringResource(R.string.feedback_per_number),
            hint = stringResource(R.string.feedback_per_number_hint),
            checked = settings.feedbackPerNumber,
            enabled = settings.vibrationEnabled || settings.soundEnabled,
            onCheckedChange = onFeedbackPerNumber,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Строка «название + подсказка + переключатель». */
@Composable
private fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    RandomNumbersTheme {
        SettingsContent(
            settings = AppSettings(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            onThemeMode = {},
            onAccent = {},
            onVibration = {},
            onSound = {},
            onFeedbackPerNumber = {},
        )
    }
}
