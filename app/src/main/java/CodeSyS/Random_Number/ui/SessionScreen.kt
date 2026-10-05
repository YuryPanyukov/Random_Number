package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.data.HistoryExporter
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.domain.DiceRoll
import CodeSyS.Random_Number.ui.session.SessionEvent
import CodeSyS.Random_Number.ui.session.SessionUiState
import CodeSyS.Random_Number.ui.session.SessionViewModel
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Доступные варианты количества чисел за одно нажатие (задача 1.1). */
private val batchSizes = listOf(1, 2, 3, 5, 10)

/** Сколько последних чисел истории показывать до нажатия «показать ещё». */
private const val HISTORY_PAGE = 30

/**
 * Экран сессии генерации: крупное число, кнопка «Сгенерировать»,
 * выбор количества чисел, прогресс «без повторений» и диалог
 * «Все числа выбраны».
 *
 * Долгое нажатие на число истории/крупное число → меню
 * «Копировать»/«Удалить»; есть шаринг и ручное добавление числа.
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
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val texts = LocalSessionTexts.current
    val documentSaver = rememberDocumentSaver()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(sessionId) {
        if (sessionId != null) viewModel.loadSession(sessionId)
    }

    // Если сессия удалена во время открытого экрана — уходим в меню.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.SessionNotFound -> onBack()

                is SessionEvent.ExportReady -> if (event.share) {
                    shareText(context, event.text)
                } else {
                    documentSaver.save(event.text, event.fileName, event.mimeType)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.session?.let { texts.title(it) } ?: stringResource(R.string.generation))
                },
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
            onUndo = viewModel::undoLast,
            onReset = viewModel::resetNumbers,
            onNewSession = onNewSession,
            onBatchSizeChange = viewModel::setBatchSize,
            onRemoveNumber = viewModel::removeNumber,
            onAddManual = viewModel::addManualNumber,
            onCopy = { clipboard.setText(AnnotatedString(it)) },
            onShare = {
                state.session?.let { session -> shareText(context, buildShareText(session, texts)) }
            },
            onToggleStats = viewModel::toggleStats,
            onSetSeed = viewModel::setSeed,
            onSaveHistory = { format -> viewModel.exportHistory(format, share = false) },
            onShareHistory = { format -> viewModel.exportHistory(format, share = true) },
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
    contentPadding: PaddingValues,
    onGenerate: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onNewSession: () -> Unit,
    onBatchSizeChange: (Int) -> Unit,
    onRemoveNumber: (Int) -> Unit,
    onAddManual: (Int) -> Unit,
    onCopy: (String) -> Unit,
    onShare: () -> Unit,
    onToggleStats: () -> Unit,
    onSetSeed: (Long?) -> Unit,
    onSaveHistory: (HistoryExporter.Format) -> Unit,
    onShareHistory: (HistoryExporter.Format) -> Unit,
) {
    val session = state.session
    val texts = LocalSessionTexts.current
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

        // Крупное последнее значение (число или элемент; долгое нажатие → «Копировать»)
        BigNumberCard(
            value = state.lastDisplay ?: "—",
            onCopy = onCopy,
        )

        // Сообщение о частичном батче: просили больше, чем осталось в диапазоне.
        if (state.lastBatchPartial) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.batch_partial_notice,
                    state.batchSize,
                    session.pickedCount,
                    session.rangeSize,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Детализация последнего броска кубиков: «4 + 2 + 6» (2.2).
        if (session.isDiceMode) {
            session.lastDiceRoll?.let { roll ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = DiceRoll.formatDetail(roll),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Последняя перестановка списка (2.4).
        if (session.isShuffleMode) {
            session.lastShuffle?.let { order ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = order.joinToString(" → "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // Выбор количества значений за одно нажатие (1.1).
        // У кубиков количество задано в сессии, у перемешивания за одно
        // нажатие выдаётся одна перестановка — выбор не нужен.
        if (!session.isDiceMode && !session.isShuffleMode) {
            Text(
                text = stringResource(R.string.batch_size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                batchSizes.forEach { size ->
                    FilterChip(
                        selected = state.batchSize == size,
                        onClick = { onBatchSizeChange(size) },
                        label = { Text(size.toString()) },
                    )
                }
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

        // Отмена последнего числа + сброс истории (1.2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.weight(1f),
                // У перемешивания перестановка — атомарная запись, убирать
                // из неё отдельный элемент смысла нет.
                enabled = session.generated.isNotEmpty() && !session.isShuffleMode,
            ) {
                Text(stringResource(R.string.undo_last))
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                enabled = session.generated.isNotEmpty(),
            ) {
                Text(stringResource(R.string.reset_numbers))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                enabled = session.generated.isNotEmpty(),
            ) {
                Text(stringResource(R.string.share))
            }
            OutlinedButton(
                onClick = onNewSession,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.new_session))
            }
        }

        // История выданных значений: ленивый список с кнопкой «показать ещё»
        if (session.generated.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            HistorySection(
                history = session.log.indices
                    .reversed()
                    .map { it to session.displayOf(session.log[it].value, texts) },
                visible = historyVisible,
                onToggle = { historyVisible = !historyVisible },
                onCopy = onCopy,
                onRemove = onRemoveNumber,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Статистика: у монеты — счёт орлов/решек, у чисел/кубиков — SessionStats.
        // Для списка и перемешивания не значима (индексы).
        if (session.isCoinMode) {
            CoinStatsSection(session = session)
            Spacer(Modifier.height(16.dp))
        } else if (!session.isItemsMode && !session.isShuffleMode) {
            OutlinedButton(
                onClick = onToggleStats,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.show_stats))
            }
            if (state.showStats) {
                Spacer(Modifier.height(8.dp))
                SessionStatsCard(stats = state.stats)
            }
            Spacer(Modifier.height(16.dp))
        }

        // Seed: фиксирует последовательность для проверки розыгрыша.
        SeedSection(session = session, onSetSeed = onSetSeed)

        Spacer(Modifier.height(8.dp))

        // Экспорт истории: файл или share-sheet в выбранном формате.
        ExportSection(onSave = onSaveHistory, onShare = onShareHistory)

        // Ручное добавление числа в историю (1.6) — только для режима чисел.
        if (session.isNumbersMode) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            ManualAddSection(session = session, onAdd = onAddManual)
        }
    }
}

/** Счётчик исходов монеты: сколько раз выпал орёл и сколько — решка. */
@Composable
private fun CoinStatsSection(session: Session) {
    Text(
        text = stringResource(
            R.string.coin_stats,
            session.coinHeadsCount,
            session.coinTailsCount,
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Поле seed: задаёт/сбрасывает воспроизводимую генерацию. */
@Composable
private fun SeedSection(
    session: Session,
    onSetSeed: (Long?) -> Unit,
) {
    var text by remember(session.seed) { mutableStateOf(session.seed?.toString().orEmpty()) }
    val value = text.toLongOrNull()
    val error = text.isNotEmpty() && value == null

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.seed),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (session.seed != null) {
                Text(
                    text = stringResource(R.string.seed_active, session.seed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.seed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error,
                supportingText = if (error) {
                    { Text(stringResource(R.string.seed_invalid)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { value?.let(onSetSeed) },
                enabled = value != null && value != session.seed,
            ) {
                Text(stringResource(R.string.seed_set))
            }
            if (session.seed != null) {
                OutlinedButton(onClick = { onSetSeed(null) }) {
                    Text(stringResource(R.string.seed_clear))
                }
            }
        }
    }
}

/**
 * Экспорт истории: выбор формата и способа — сохранить в файл
 * (`ACTION_CREATE_DOCUMENT`) или отдать через share-sheet.
 */
@Composable
private fun ExportSection(
    onSave: (HistoryExporter.Format) -> Unit,
    onShare: (HistoryExporter.Format) -> Unit,
) {
    var format by remember { mutableStateOf(HistoryExporter.Format.CSV) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.export_history),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryExporter.Format.entries.forEach { candidate ->
                FilterChip(
                    selected = format == candidate,
                    onClick = { format = candidate },
                    label = { Text(candidate.extension.uppercase()) },
                )
            }
        }
        Text(
            text = stringResource(format.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onSave(format) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.export_save))
            }
            OutlinedButton(
                onClick = { onShare(format) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.share))
            }
        }
    }
}

/** Локализованное описание формата экспорта. */
private fun HistoryExporter.Format.labelRes(): Int = when (this) {
    HistoryExporter.Format.CSV -> R.string.export_format_csv
    HistoryExporter.Format.TXT -> R.string.export_format_txt
    HistoryExporter.Format.JSON -> R.string.export_format_json
    HistoryExporter.Format.MARKDOWN -> R.string.export_format_markdown
}

/** Крупное число с долгим нажатием → меню «Копировать». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigNumberCard(
    value: String,
    onCopy: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.copy)
    val longPressHint = stringResource(R.string.long_press_hint)

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
                text = stringResource(R.string.last_result),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Box {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        // TalkBack озвучивает результат сразу после генерации.
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "$value. $longPressHint"
                        }
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuOpen = true },
                            onLongClickLabel = copyLabel,
                        ),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(copyLabel) },
                        onClick = {
                            onCopy(value)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * История сгенерированных чисел: чипы с долгим нажатием —
 * меню «Копировать»/«Удалить» (1.3, 1.6).
 *
 * Список ленивый и постраничный: длинная история не тормозит экран.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HistorySection(
    history: List<Pair<Int, String>>,
    visible: Boolean,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    // Индекс в журнале и значение: при изменении истории меню не
    // «перепрыгнет» на чужую запись.
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var menuValue by remember { mutableStateOf<String?>(null) }
    var shown by remember(history.size) { mutableIntStateOf(HISTORY_PAGE) }
    val listState = rememberLazyListState()
    val longPressHint = stringResource(R.string.long_press_hint)

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
            text = pluralStringResource(
                R.plurals.history_count,
                history.size,
                history.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onToggle) {
            Text(stringResource(if (visible) R.string.history_hide else R.string.history_show))
        }
    }

    if (visible) {
        val visibleCount = shown.coerceAtMost(history.size)
        // Новые значения — первыми.
        val page = remember(history.size, visibleCount) { history.take(visibleCount) }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = page, key = { it.first }) { entry ->
                val index = entry.first
                val value = entry.second
                val deleteLabel = stringResource(R.string.delete)
                val copyLabel = stringResource(R.string.copy)
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .semantics {
                                contentDescription = "$value. $longPressHint"
                            }
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    menuIndex = index
                                    menuValue = value
                                },
                                onLongClickLabel = copyLabel,
                            ),
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuIndex == index && menuValue == value,
                        onDismissRequest = {
                            menuIndex = null
                            menuValue = null
                        },
                    ) {
                        DropdownMenuItem(
                            text = { Text(copyLabel) },
                            onClick = {
                                onCopy(value)
                                menuIndex = null
                                menuValue = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(deleteLabel) },
                            onClick = {
                                onRemove(index)
                                menuIndex = null
                                menuValue = null
                            },
                        )
                    }
                }
            }
        }

        if (visibleCount < history.size) {
            TextButton(onClick = { shown += HISTORY_PAGE }) {
                Text(stringResource(R.string.history_show_more, history.size - visibleCount))
            }
        }
    }
}

/** Ручное добавление числа в историю с валидацией (1.6). */
@Composable
private fun ManualAddSection(
    session: Session,
    onAdd: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val value = text.toIntOrNull()
    val outOfRange = value != null && value !in session.min..session.max
    val duplicate = value != null &&
        !outOfRange &&
        !session.allowRepeats &&
        value in session.generated

    val errorText = when {
        text.isEmpty() -> null
        value == null -> stringResource(R.string.invalid_number)
        outOfRange -> stringResource(R.string.out_of_range, session.min, session.max)
        duplicate -> stringResource(R.string.duplicate_number)
        else -> null
    }
    val canAdd = value != null && errorText == null

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.add_manual)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = errorText != null,
            supportingText = if (errorText != null) {
                { Text(errorText) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                if (canAdd) {
                    onAdd(value)
                    text = ""
                }
            },
            enabled = canAdd,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_manual),
            )
        }
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
    RandomNumbersTheme {
        SessionContent(
            state = SessionUiState(isLoading = false),
            contentPadding = PaddingValues(0.dp),
            onGenerate = {},
            onUndo = {},
            onReset = {},
            onNewSession = {},
            onBatchSizeChange = {},
            onRemoveNumber = {},
            onAddManual = {},
            onCopy = {},
            onShare = {},
            onToggleStats = {},
            onSetSeed = {},
            onSaveHistory = {},
            onShareHistory = {},
        )
    }
}
