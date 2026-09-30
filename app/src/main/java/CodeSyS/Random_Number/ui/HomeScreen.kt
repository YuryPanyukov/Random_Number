package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.ui.home.HomeEvent
import CodeSyS.Random_Number.ui.home.HomeUiState
import CodeSyS.Random_Number.ui.home.HomeViewModel
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Главный экран: список сохранённых сессий + кнопка «Новая сессия».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }

    // Строки читаем в момент композиции: LocalContext не реагирует
    // на смену конфигурации, и snackbar показал бы устаревший текст.
    val importDoneTemplate = stringResource(R.string.import_done)
    val importFailedText = stringResource(R.string.import_failed)

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val count = text?.let { viewModel.importSessions(it) } ?: 0
            snackbarHostState.showSnackbar(
                if (count > 0) {
                    importDoneTemplate.format(count)
                } else {
                    importFailedText
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.ExportReady -> {
                    val intent = createSaveDocumentIntent(
                        text = event.text,
                        fileName = event.fileName,
                        mimeType = event.mimeType,
                    )
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }
                            .onFailure { shareText(context, event.text) }
                    } else {
                        shareText(context, event.text)
                    }
                }

                is HomeEvent.Imported -> snackbarHostState.showSnackbar(
                    importDoneTemplate.format(event.count),
                )

                HomeEvent.ImportFailed -> snackbarHostState.showSnackbar(
                    importFailedText,
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = { openDocument.launch(arrayOf("application/json", "text/plain")) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = stringResource(R.string.import_sessions),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.exportAllSessions() },
                        enabled = state.sessions.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export_sessions),
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewSession) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_session))
            }
        },
    ) { innerPadding ->
        if (state.sessions.isEmpty() && !state.isLoading) {
            EmptyContent(modifier = Modifier.padding(innerPadding))
        } else {
            SessionList(
                state = state,
                contentPadding = innerPadding,
                onOpenSession = onOpenSession,
                onDeleteRequest = { sessionToDelete = it },
            )
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_message, session.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        sessionToDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Пустое состояние главного экрана (нет ни одной сессии). */
@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.no_sessions),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_sessions_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Список сохранённых сессий: карточка + удаление с подтверждением. */
@Composable
private fun SessionList(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onDeleteRequest: (Session) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.sessions, key = { it.id }) { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = { onOpenSession(session.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        val progress = if (session.allowRepeats) {
                            stringResource(
                                R.string.session_generated_count,
                                session.generated.size,
                            )
                        } else {
                            stringResource(
                                R.string.session_progress,
                                session.pickedCount,
                                session.rangeSize,
                            )
                        }
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatTimestamp(session.lastUsedAt, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onDeleteRequest(session) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) } // место под FAB
    }
}

/**
 * Форматирует время последнего использования сессии по локали устройства.
 *
 * Формат берётся из [java.text.DateFormat] (по умолчанию для [locale]),
 * а не из жёстко заданной строки — иначе дата выглядела бы одинаково
 * для всех языков.
 */
internal fun formatTimestamp(epochMillis: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        .format(Date(epochMillis))

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RandomNumbersTheme {
        HomeScreen(onNewSession = {}, onOpenSession = {})
    }
}
