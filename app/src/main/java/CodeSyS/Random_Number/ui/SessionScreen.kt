package CodeSyS.Random_Number.ui

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.ui.session.SessionEvent
import CodeSyS.Random_Number.ui.session.SessionUiState
import CodeSyS.Random_Number.ui.session.SessionViewModel
import CodeSyS.Random_Number.ui.theme.СлучайныеЧислаTheme

/**
 * Экран сессии генерации: крупное число, кнопка «Сгенерировать»,
 * прогресс «без повторений» и диалог «Все числа выбраны».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    sessionId: String?,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        if (sessionId != null) viewModel.loadSession(sessionId)
    }

    // Если сессия удалена во время открытого экрана — уходим в меню.
    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it is SessionEvent.SessionNotFound) onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.session?.title ?: stringResource(R.string.generation)) },
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
        SessionContent(
            state = state,
            contentPadding = innerPadding,
            onGenerate = viewModel::generate,
            onReset = viewModel::resetNumbers,
            onNewSession = onNewSession,
        )
    }

    if (state.showExhaustedDialog) {
        ExhaustedDialog(
            onNewSession = {
                viewModel.dismissExhaustedDialog()
                onNewSession()
            },
            onResetContinue = viewModel::resetNumbers,
            onDismiss = viewModel::dismissExhaustedDialog,
        )
    }
}

/** Содержимое экрана сессии: число, прогресс, кнопки и история. */
@Composable
private fun SessionContent(
    state: SessionUiState,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onGenerate: () -> Unit,
    onReset: () -> Unit,
    onNewSession: () -> Unit,
) {
    val session = state.session
    var historyVisible by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (session == null) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
            )
            return
        }

        // Крупное последнее число
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.last_number),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.lastNumber?.toString() ?: "—",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Прогресс для режима «без повторений»
        if (!session.allowRepeats) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.session_progress,
                        session.pickedCount,
                        session.rangeSize,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        session.pickedCount.toFloat() / session.rangeSize.coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(
                text = stringResource(R.string.generate),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                enabled = session.generated.isNotEmpty(),
            ) {
                Text(stringResource(R.string.reset_numbers))
            }
            OutlinedButton(
                onClick = onNewSession,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.new_session))
            }
        }

        // История выданных чисел: полный список с возможностью скрытия
        if (session.generated.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            HistorySection(
                generated = session.generated,
                visible = historyVisible,
                onToggle = { historyVisible = !historyVisible },
            )
        }
    }
}

/** Полная история сгенерированных чисел со скрытием/показом по кнопке. */
@Composable
private fun HistorySection(
    generated: List<Int>,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.session_generated_count, generated.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onToggle) {
            Text(
                stringResource(if (visible) R.string.history_hide else R.string.history_show),
            )
        }
    }

    if (visible) {
        // Новые числа — первыми; весь список прокручивается вместе с экраном.
        Text(
            text = generated.reversed().joinToString("  "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

/** Диалог «Все числа выбраны»: новая сессия или сброс текущей. */
@Composable
private fun ExhaustedDialog(
    onNewSession: () -> Unit,
    onResetContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exhausted_title)) },
        text = { Text(stringResource(R.string.exhausted_message)) },
        confirmButton = {
            TextButton(onClick = onNewSession) { Text(stringResource(R.string.new_session)) }
        },
        dismissButton = {
            TextButton(onClick = onResetContinue) { Text(stringResource(R.string.continue_reset)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SessionScreenPreview() {
    СлучайныеЧислаTheme {
        SessionContent(
            state = SessionUiState(isLoading = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            onGenerate = {},
            onReset = {},
            onNewSession = {},
        )
    }
}
