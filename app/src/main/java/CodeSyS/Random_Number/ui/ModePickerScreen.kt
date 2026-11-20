package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.ui.newsession.NewSessionMode
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Экран выбора режима генерации (задача 2.6).
 *
 * Первый шаг создания сессии: пользователь выбирает, *что* генерировать,
 * а форма [NewSessionScreen] затем спрашивает только параметры режима.
 *
 * Экран не хранит состояние (список режимов фиксирован) — это чистый
 * stateless-композабл, который сообщает выбранный режим наружу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModePickerScreen(
    onBack: () -> Unit,
    onModeSelected: (NewSessionMode) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mode_picker_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
        ModePickerContent(
            contentPadding = innerPadding,
            onModeSelected = onModeSelected,
        )
    }
}

/** Список карточек режимов: название + краткое пояснение. */
@Composable
private fun ModePickerContent(
    contentPadding: PaddingValues,
    onModeSelected: (NewSessionMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NewSessionMode.entries.forEach { mode ->
            ModeCard(mode = mode, onClick = { onModeSelected(mode) })
        }
    }
}

/** Карточка одного режима: тап открывает форму с предвыбранным режимом. */
@Composable
private fun ModeCard(
    mode: NewSessionMode,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(mode.hintRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Локализованное название режима (совпадает с чипами на форме). */
private fun NewSessionMode.titleRes(): Int = when (this) {
    NewSessionMode.NUMBERS -> R.string.mode_numbers
    NewSessionMode.ITEMS -> R.string.mode_items
    NewSessionMode.DICE -> R.string.mode_dice
    NewSessionMode.COIN -> R.string.mode_coin
    NewSessionMode.SHUFFLE -> R.string.mode_shuffle
}

/** Локализованное пояснение режима для карточки выбора. */
private fun NewSessionMode.hintRes(): Int = when (this) {
    NewSessionMode.NUMBERS -> R.string.mode_numbers_hint
    NewSessionMode.ITEMS -> R.string.mode_items_hint
    NewSessionMode.DICE -> R.string.mode_dice_hint
    NewSessionMode.COIN -> R.string.mode_coin_hint
    NewSessionMode.SHUFFLE -> R.string.mode_shuffle_hint
}

@Preview(showBackground = true)
@Composable
private fun ModePickerScreenPreview() {
    RandomNumbersTheme {
        ModePickerContent(
            contentPadding = PaddingValues(0.dp),
            onModeSelected = {},
        )
    }
}
