package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.domain.RangePreset
import CodeSyS.Random_Number.ui.newsession.NewSessionMode
import CodeSyS.Random_Number.ui.newsession.NewSessionUiState
import CodeSyS.Random_Number.ui.newsession.NewSessionViewModel
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Экран параметров новой сессии: пресеты диапазонов, диапазон «от…до»
 * и флаг «без повторений».
 *
 * Состояние формы — в [NewSessionViewModel], поэтому переживает
 * пересоздание Activity.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSessionScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    initialMode: NewSessionMode = NewSessionMode.NUMBERS,
    viewModel: NewSessionViewModel = viewModel(
        factory = remember(initialMode) { newSessionViewModelFactory(initialMode) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_session)) },
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
        NewSessionForm(
            state = state,
            contentPadding = innerPadding,
            onModeChange = viewModel::setMode,
            onMinChange = viewModel::setMin,
            onMaxChange = viewModel::setMax,
            onItemsChange = viewModel::setItemsText,
            onDiceCountChange = viewModel::setDiceCount,
            onDiceSidesChange = viewModel::setDiceSides,
            onWithoutRepeatsChange = viewModel::setWithoutRepeats,
            onPreset = viewModel::applyPreset,
            onCreate = {
                if (viewModel.createSession()) onCreated()
            },
        )
    }
}

/** Содержимое формы: пресеты, поля диапазона, переключатель и кнопка. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewSessionForm(
    state: NewSessionUiState,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onModeChange: (NewSessionMode) -> Unit,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onItemsChange: (String) -> Unit,
    onDiceCountChange: (String) -> Unit,
    onDiceSidesChange: (String) -> Unit,
    onWithoutRepeatsChange: (Boolean) -> Unit,
    onPreset: (Int, Int, Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Пресеты диапазонов: быстрый тап заполняет форму (1.5).
        Text(
            text = stringResource(R.string.presets),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RangePreset.entries.forEach { preset ->
                AssistChip(
                    onClick = { onPreset(preset.min, preset.max, preset.allowRepeats) },
                    label = { Text(stringResource(preset.titleRes())) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Режим создания: числа, список элементов (2.1) или кубики (2.2).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == NewSessionMode.NUMBERS,
                onClick = { onModeChange(NewSessionMode.NUMBERS) },
                label = { Text(stringResource(R.string.mode_numbers)) },
            )
            FilterChip(
                selected = state.mode == NewSessionMode.ITEMS,
                onClick = { onModeChange(NewSessionMode.ITEMS) },
                label = { Text(stringResource(R.string.mode_items)) },
            )
            FilterChip(
                selected = state.mode == NewSessionMode.DICE,
                onClick = { onModeChange(NewSessionMode.DICE) },
                label = { Text(stringResource(R.string.mode_dice)) },
            )
            FilterChip(
                selected = state.mode == NewSessionMode.COIN,
                onClick = { onModeChange(NewSessionMode.COIN) },
                label = { Text(stringResource(R.string.mode_coin)) },
            )
            FilterChip(
                selected = state.mode == NewSessionMode.SHUFFLE,
                onClick = { onModeChange(NewSessionMode.SHUFFLE) },
                label = { Text(stringResource(R.string.mode_shuffle)) },
            )
        }

        Spacer(Modifier.height(16.dp))

        if (state.mode == NewSessionMode.NUMBERS) {
            OutlinedTextField(
                value = state.minText,
                onValueChange = onMinChange,
                label = { Text(stringResource(R.string.range_from)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.minError,
                supportingText = if (state.minError) {
                    { Text(stringResource(R.string.invalid_number)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.maxText,
                onValueChange = onMaxChange,
                label = { Text(stringResource(R.string.range_to)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.maxError || state.rangeError,
                supportingText = {
                    when {
                        state.maxError -> Text(stringResource(R.string.invalid_number))
                        state.rangeError -> Text(stringResource(R.string.range_error))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.mode == NewSessionMode.ITEMS || state.mode == NewSessionMode.SHUFFLE) {
            OutlinedTextField(
                value = state.itemsText,
                onValueChange = onItemsChange,
                label = {
                    Text(
                        stringResource(
                            if (state.mode == NewSessionMode.SHUFFLE) {
                                R.string.shuffle_label
                            } else {
                                R.string.items_label
                            },
                        ),
                    )
                },
                supportingText = {
                    Text(
                        stringResource(
                            if (state.mode == NewSessionMode.SHUFFLE) {
                                R.string.shuffle_hint
                            } else {
                                R.string.items_hint
                            },
                        ),
                    )
                },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.mode == NewSessionMode.DICE) {
            OutlinedTextField(
                value = state.diceCountText,
                onValueChange = onDiceCountChange,
                label = { Text(stringResource(R.string.dice_count)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.diceCountText.isNotEmpty() && (state.diceCount ?: 0) < 1,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.diceSidesText,
                onValueChange = onDiceSidesChange,
                label = { Text(stringResource(R.string.dice_sides)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.diceSidesText.isNotEmpty() && (state.diceSides ?: 0) < 2,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Монета: пара «орёл/решка», кроме выбора режима ничего не нужно.
            Text(
                text = stringResource(R.string.coin_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Кубики, монета и перемешивание повторяются — переключатель повторов им не нужен.
        if (
            state.mode != NewSessionMode.DICE &&
            state.mode != NewSessionMode.COIN &&
            state.mode != NewSessionMode.SHUFFLE
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.allow_repeats),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.allow_repeats_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.withoutRepeats,
                    onCheckedChange = onWithoutRepeatsChange,
                )
            }
        }

        if (state.mode == NewSessionMode.NUMBERS && state.withoutRepeats && state.canCreate) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.session_progress,
                    0,
                    state.rangeSize,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onCreate,
            enabled = state.canCreate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.start_session))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewSessionScreenPreview() {
    RandomNumbersTheme {
        NewSessionForm(
            state = NewSessionUiState(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            onModeChange = {},
            onMinChange = {},
            onMaxChange = {},
            onItemsChange = {},
            onDiceCountChange = {},
            onDiceSidesChange = {},
            onWithoutRepeatsChange = {},
            onPreset = { _, _, _ -> },
            onCreate = {},
        )
    }
}

/** Локализованное название пресета. */
@Composable
private fun RangePreset.titleRes(): Int = when (this) {
    RangePreset.DICE -> R.string.preset_dice
    RangePreset.COIN -> R.string.preset_coin
    RangePreset.LOTTERY -> R.string.preset_lottery
    RangePreset.HUNDRED -> R.string.preset_hundred
}
