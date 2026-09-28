package CodeSyS.Random_Number.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.ui.theme.СлучайныеЧислаTheme

/**
 * Экран параметров новой сессии: диапазон «от…до» и флаг «без повторений».
 *
 * @param onCreated вызывается с корректными параметрами после валидации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(
    onBack: () -> Unit,
    onCreated: (min: Int, max: Int, allowRepeats: Boolean) -> Unit,
) {
    var minText by remember { mutableStateOf("1") }
    var maxText by remember { mutableStateOf("100") }
    // Свитч называется «Без повторений», поэтому храним именно его значение:
    // включён = числа не повторяются (allowRepeats = false).
    var withoutRepeats by remember { mutableStateOf(true) }
    var minTouched by remember { mutableStateOf(false) }
    var maxTouched by remember { mutableStateOf(false) }

    val min = minText.toIntOrNull()
    val max = maxText.toIntOrNull()
    val minError = minTouched && (min == null || min > Int.MAX_VALUE)
    val maxError = maxTouched && (max == null)
    val rangeError = min != null && max != null && min > max
    val canCreate = min != null && max != null && !rangeError

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = minText,
                onValueChange = {
                    minText = it
                    minTouched = true
                },
                label = { Text(stringResource(R.string.range_from)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = minError,
                supportingText = if (minError) {
                    { Text(stringResource(R.string.invalid_number)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = maxText,
                onValueChange = {
                    maxText = it
                    maxTouched = true
                },
                label = { Text(stringResource(R.string.range_to)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = maxError || rangeError,
                supportingText = {
                    when {
                        maxError -> Text(stringResource(R.string.invalid_number))
                        rangeError -> Text(stringResource(R.string.range_error))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

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
                    checked = withoutRepeats,
                    onCheckedChange = { withoutRepeats = it },
                )
            }

            if (withoutRepeats && min != null && max != null && !rangeError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.session_progress,
                        0,
                        max.toLong() - min.toLong() + 1,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onCreated(min!!, max!!, !withoutRepeats) },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.start_session))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewSessionScreenPreview() {
    СлучайныеЧислаTheme {
        NewSessionScreen(onBack = {}, onCreated = { _, _, _ -> })
    }
}
