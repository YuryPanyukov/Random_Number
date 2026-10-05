package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.ui.home.HomeEvent
import CodeSyS.Random_Number.ui.home.HomeUiState
import CodeSyS.Random_Number.ui.home.HomeViewModel
import CodeSyS.Random_Number.ui.home.SessionFilter
import CodeSyS.Random_Number.ui.home.SessionSort
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
 * Главный экран: поиск/фильтр/сортировка сессий, карточка честного розыгрыша
 * и список сохранённых сессий.
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
    val texts = LocalSessionTexts.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val documentSaver = rememberDocumentSaver()
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var sessionToTag by remember { mutableStateOf<Session?>(null) }
    var honestListOpen by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    // Строки читаем в момент композиции: LocalContext не реагирует
    // на смену конфигурации, и snackbar показал бы устаревший текст.
    val importDoneTemplate = stringResource(R.string.import_done)
    val importFailedText = stringResource(R.string.import_failed)
    val honestDrawFailedText = stringResource(R.string.honest_draw_failed)
    val sessionDuplicatedText = stringResource(R.string.session_duplicated)

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
                is HomeEvent.ExportReady -> if (event.share) {
                    shareText(context, event.text)
                } else {
                    documentSaver.save(event.text, event.fileName, event.mimeType)
                }

                is HomeEvent.Imported -> snackbarHostState.showSnackbar(
                    importDoneTemplate.format(event.count),
                )

                HomeEvent.ImportFailed -> snackbarHostState.showSnackbar(
                    importFailedText,
                )

                is HomeEvent.HonestDrawReady -> shareText(
                    context,
                    buildShareText(event.session, texts),
                )

                HomeEvent.HonestDrawFailed -> snackbarHostState.showSnackbar(
                    honestDrawFailedText,
                )

                HomeEvent.SessionDuplicated -> snackbarHostState.showSnackbar(
                    sessionDuplicatedText,
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
                    Box {
                        IconButton(
                            onClick = { exportMenuOpen = true },
                            enabled = state.sessions.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.export_sessions),
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_sessions)) },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.exportAllSessions(share = false)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.exportAllSessions(share = true)
                                },
                            )
                        }
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
        HomeContent(
            state = state,
            contentPadding = innerPadding,
            onOpenSession = onOpenSession,
            onDeleteRequest = { sessionToDelete = it },
            onDuplicate = { viewModel.duplicateSession(it.id) },
            onToggleFavorite = { viewModel.toggleFavorite(it.id) },
            onToggleArchived = { viewModel.toggleArchived(it.id) },
            onEditTags = { sessionToTag = it },
            onQueryChange = viewModel::setQuery,
            onFilterChange = viewModel::setFilter,
            onSortChange = viewModel::setSort,
            onHonestNumber = viewModel::drawHonestNumber,
            onHonestList = { honestListOpen = true },
        )
    }

    if (honestListOpen) {
        HonestDrawListDialog(
            onDismiss = { honestListOpen = false },
            onConfirm = { items ->
                honestListOpen = false
                viewModel.drawHonestFromList(items)
            },
        )
    }

    sessionToTag?.let { session ->
        TagsDialog(
            initial = session.tags,
            onDismiss = { sessionToTag = null },
            onConfirm = { tags ->
                viewModel.setTags(session.id, tags)
                sessionToTag = null
            },
        )
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_message, texts.title(session))) },
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
            .fillMaxWidth()
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

/** Подсказка, когда поиск/фильтр не дал результатов. */
@Composable
private fun NothingFound() {
    Text(
        text = stringResource(R.string.nothing_found),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}

/**
 * Содержимое главного экрана: поиск/сортировка, чипы фильтра, карточка
 * честного розыгрыша и список сессий. Долгое нажатие на карточку открывает
 * меню действий.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onDeleteRequest: (Session) -> Unit,
    onDuplicate: (Session) -> Unit,
    onToggleFavorite: (Session) -> Unit,
    onToggleArchived: (Session) -> Unit,
    onEditTags: (Session) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SessionFilter) -> Unit,
    onSortChange: (SessionSort) -> Unit,
    onHonestNumber: () -> Unit,
    onHonestList: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val texts = LocalSessionTexts.current
    var menuSessionId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SearchAndSortRow(
                query = state.query,
                sort = state.sort,
                onQueryChange = onQueryChange,
                onSortChange = onSortChange,
            )
        }
        item {
            FilterChipsRow(filter = state.filter, onFilterChange = onFilterChange)
        }
        item {
            HonestDrawCard(onNumber = onHonestNumber, onList = onHonestList)
        }

        if (state.sessions.isEmpty() && !state.isLoading) {
            item { if (state.hasActiveSearch) NothingFound() else EmptyContent() }
            item { Spacer(Modifier.height(72.dp)) }
            return@LazyColumn
        }

        items(state.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                locale = locale,
                menuOpen = menuSessionId == session.id,
                onOpen = { onOpenSession(session.id) },
                onOpenMenu = { menuSessionId = session.id },
                onDismissMenu = { menuSessionId = null },
                onDuplicate = { onDuplicate(session) },
                onToggleFavorite = { onToggleFavorite(session) },
                onToggleArchived = { onToggleArchived(session) },
                onEditTags = { onEditTags(session) },
                onDeleteRequest = { onDeleteRequest(session) },
                onTagClick = { onQueryChange(it) },
                title = texts.title(session),
            )
        }
        item { Spacer(Modifier.height(72.dp)) } // место под FAB
    }
}

/** Поиск по заголовку/тегам + выбор сортировки. */
@Composable
private fun SearchAndSortRow(
    query: String,
    sort: SessionSort,
    onQueryChange: (String) -> Unit,
    onSortChange: (SessionSort) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text(stringResource(sort.labelRes()))
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                SessionSort.entries.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(stringResource(candidate.labelRes())) },
                        onClick = {
                            sortMenuOpen = false
                            onSortChange(candidate)
                        },
                    )
                }
            }
        }
    }
}

/** Чипы фильтра списка: все / избранное / архив. */
@Composable
private fun FilterChipsRow(
    filter: SessionFilter,
    onFilterChange: (SessionFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionFilter.entries.forEach { candidate ->
            FilterChip(
                selected = filter == candidate,
                onClick = { onFilterChange(candidate) },
                label = { Text(stringResource(candidate.labelRes())) },
            )
        }
    }
}

/** Карточка одной сессии: заголовок, прогресс, теги и действия. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    session: Session,
    locale: Locale,
    menuOpen: Boolean,
    title: String,
    onOpen: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchived: () -> Unit,
    onEditTags: () -> Unit,
    onDeleteRequest: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val duplicateLabel = stringResource(R.string.duplicate)
    val deleteLabel = stringResource(R.string.delete)
    val favoriteLabel = stringResource(
        if (session.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites,
    )
    val archivedLabel = stringResource(
        if (session.isArchived) R.string.unarchive else R.string.archive,
    )
    val tagsLabel = stringResource(R.string.tags_title)

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onOpenMenu,
                    onLongClickLabel = duplicateLabel,
                ),
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    val progress = if (session.allowRepeats) {
                        stringResource(R.string.session_generated_count, session.generated.size)
                    } else {
                        stringResource(R.string.session_progress, session.pickedCount, session.rangeSize)
                    }
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (session.tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            session.tags.forEach { tag ->
                                AssistChip(
                                    onClick = { onTagClick(tag) },
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }
                    Text(
                        text = formatTimestamp(session.lastUsedAt, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (session.isFavorite) {
                            Icons.Default.Star
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = favoriteLabel,
                        tint = if (session.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onDeleteRequest) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = deleteLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text(duplicateLabel) }, onClick = onDuplicate)
            DropdownMenuItem(text = { Text(favoriteLabel) }, onClick = onToggleFavorite)
            DropdownMenuItem(text = { Text(archivedLabel) }, onClick = onToggleArchived)
            DropdownMenuItem(text = { Text(tagsLabel) }, onClick = onEditTags)
            DropdownMenuItem(text = { Text(deleteLabel) }, onClick = onDeleteRequest)
        }
    }
}

/** Локализованное название фильтра. */
private fun SessionFilter.labelRes(): Int = when (this) {
    SessionFilter.ALL -> R.string.filter_all
    SessionFilter.FAVORITES -> R.string.filter_favorites
    SessionFilter.ARCHIVE -> R.string.filter_archive
}

/** Локализованное название сортировки. */
private fun SessionSort.labelRes(): Int = when (this) {
    SessionSort.DATE -> R.string.sort_date
    SessionSort.TITLE -> R.string.sort_title
    SessionSort.PROGRESS -> R.string.sort_progress
}

/**
 * Карточка «Честный розыгрыш»: две кнопки — число 1–100 и розыгрыш
 * из введённого списка. Результат создаётся сразу и уходит в share-sheet
 * вместе с seed и источником (раздел 6).
 */
@Composable
private fun HonestDrawCard(
    onNumber: () -> Unit,
    onList: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.honest_draw_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.honest_draw_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNumber,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.honest_draw_number))
                }
                OutlinedButton(
                    onClick = onList,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.honest_draw_list))
                }
            }
        }
    }
}

/**
 * Диалог ввода участников честного розыгрыша.
 *
 * Список парсится построчно: пустые строки и дубликаты отбрасываются;
 * для розыгрыша нужно минимум два участника.
 */
@Composable
private fun HonestDrawListDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val items = remember(text) {
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
    val notEnough = text.isNotBlank() && items.size < 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.honest_draw_list_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.honest_draw_list_label)) },
                supportingText = {
                    val hintRes = if (notEnough) {
                        R.string.honest_draw_list_min
                    } else {
                        R.string.honest_draw_list_hint
                    }
                    Text(stringResource(hintRes))
                },
                isError = notEnough,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(items) },
                enabled = items.size >= 2,
            ) {
                Text(stringResource(R.string.honest_draw_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Диалог редактирования тегов сессии (3.5). */
@Composable
private fun TagsDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.tags_label)) },
                supportingText = { Text(stringResource(R.string.tags_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        text.split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
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
