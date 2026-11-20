package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.data.HistoryExporter
import CodeSyS.Random_Number.data.Session
import CodeSyS.Random_Number.data.SessionPayload
import CodeSyS.Random_Number.domain.CoinSide
import CodeSyS.Random_Number.domain.DiceRoll
import CodeSyS.Random_Number.ui.session.SessionEvent
import CodeSyS.Random_Number.ui.session.SessionUiState
import CodeSyS.Random_Number.ui.session.SessionViewModel
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                actions = {
                    IconButton(onClick = viewModel::showEditDialog) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_session),
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
            onRemoveRoll = viewModel::removeRoll,
            onAddManual = viewModel::addManualNumber,
            onCopy = { clipboard.setText(AnnotatedString(it)) },
            onShare = {
                state.session?.let { session -> shareText(context, buildShareText(session, texts)) }
            },
            onShareImage = {
                val value = state.lastDisplay
                val session = state.session
                if (session != null && value != null) {
                    shareResultCardImage(
                        context,
                        buildResultCardBitmap(title = texts.title(session), value = value),
                    )
                }
            },
            onToggleStats = viewModel::toggleStats,
            onSetSeed = viewModel::setSeed,
            onDescriptionChange = viewModel::setDescription,
            onSaveHistory = { format -> viewModel.exportHistory(format, share = false) },
            onShareHistory = { format -> viewModel.exportHistory(format, share = true) },
        )
    }

    if (state.showEditDialog) {
        EditSessionDialog(
            state = state,
            onTitleChange = { _ -> /* handled internally */ },
            onDescriptionChange = viewModel::setDescription,
            onMinChange = { _ -> /* handled internally */ },
            onMaxChange = { _ -> /* handled internally */ },
            onAllowRepeatsChange = { _ -> /* handled internally */ },
            onItemsChange = { _ -> /* handled internally */ },
            onDiceCountChange = { _ -> /* handled internally */ },
            onDiceSidesChange = { _ -> /* handled internally */ },
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::hideEditDialog,
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
    onRemoveRoll: (Int, Int) -> Unit,
    onAddManual: (Int) -> Unit,
    onCopy: (String) -> Unit,
    onShare: () -> Unit,
    onShareImage: () -> Unit,
    onToggleStats: () -> Unit,
    onSetSeed: (Long?) -> Unit,
    onSaveHistory: (HistoryExporter.Format) -> Unit,
    onShareHistory: (HistoryExporter.Format) -> Unit,
    onDescriptionChange: (String) -> Unit,
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

        // Заметка к сессии (3.4)
        var descriptionVisible by remember { mutableStateOf(session.description.isNotEmpty()) }
        var descriptionText by remember { mutableStateOf(session.description) }
        if (descriptionVisible) {
            OutlinedTextField(
                value = descriptionText,
                onValueChange = {
                    descriptionText = it
                    onDescriptionChange(it)
                },
                label = { Text(stringResource(R.string.session_description_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 1,
                maxLines = 3,
                trailingIcon = {
                    IconButton(onClick = { descriptionVisible = false }) {
                        Text("✕", style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
        } else if (session.description.isNotEmpty()) {
            Text(
                text = session.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        // Крупный результат режима с знанием: монеты — монеты, у
        // кубиков — ряд кубиков; иначе крупное число (долгота: копирование)
        when {
            session.isCoinMode && state.lastDisplay != null -> {
                CoinResultCard(
                    sides = session.generated.takeLast(state.batchSize).map { CoinSide.of(it) },
                    nonce = session.generatedCount,
                    sideLabel = { side -> texts.coinSide(side) },
                    onCopy = onCopy,
                )
            }

            session.isDiceMode && session.lastDiceRoll != null -> {
                DiceResultCard(
                    values = session.lastDiceRoll!!,
                    diceFaces = (session.payload as SessionPayload.Dice).sides,
                    nonce = session.generatedCount,
                    onCopy = onCopy,
                )
            }

            session.isShuffleMode && state.lastDisplay != null -> {
                ShuffleResultCard(
                    items = session.lastShuffle(texts).orEmpty(),
                    nonce = session.generatedCount,
                    onCopy = onCopy,
                )
            }

            else -> BigNumberCard(
                value = state.lastDisplay ?: "—",
                onCopy = onCopy,
            )
        }

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
            // PNG-карточка результата для соцсетей и мессенджеров.
            OutlinedButton(
                onClick = onShareImage,
                modifier = Modifier.weight(1f),
                enabled = state.lastDisplay != null,
            ) {
                Text(stringResource(R.string.share_image))
            }
        }
        OutlinedButton(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.new_session))
        }

        // История выданных значений: ленивый список с кнопкой «показать ещё».
        // У кубиков записи группируются по броскам: чип = «сумма (4 · 2 · 6)».
        if (session.generated.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            val diceCount = (session.payload as? SessionPayload.Dice)?.count ?: 1
            val historyEntries: List<HistoryEntry> = if (session.isDiceMode && diceCount > 1) {
                session.log.chunked(diceCount)
                    .reversed()
                    .mapIndexed { fromEnd, chunk ->
                        HistoryEntry(
                            // Индекс первой записи броска в журнале.
                            index = session.log.size - chunk.size - fromEnd * diceCount,
                            display = run {
                                val values = chunk.map { it.value }
                                val sum = values.sum()
                                if (chunk.size > 1) "$sum (${values.joinToString(" · ")})" else "$sum"
                            },
                            count = chunk.size,
                        )
                    }
            } else {
                session.log.indices.reversed().map { entryIndex ->
                    HistoryEntry(
                        index = entryIndex,
                        display = session.displayOf(session.log[entryIndex].value, texts),
                        count = 1,
                    )
                }
            }
            HistorySection(
                history = historyEntries,
                visible = historyVisible,
                onToggle = { historyVisible = !historyVisible },
                onCopy = onCopy,
                onRemove = onRemoveRoll,
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
                // Гистограмма распределения (3.10): числа и значения кубиков.
                if (session.generated.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SessionHistogram(
                        values = session.generated,
                        min = session.min,
                        max = session.max,
                    )
                }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.last_result),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
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
 * Запись истории: первый индекс в журнале, готовый к показу текст и
 * сколько записей журнала убирает «Удалить» (1 у обычных значений,
 * целый бросок у кубиков).
 */
private data class HistoryEntry(
    val index: Int,
    val display: String,
    val count: Int,
)

/**
 * История сгенерированных значений: чипы с долгим нажатием —
 * меню «Копировать»/«Удалить» (1.3, 1.6). У кубиков чип — целый бросок.
 *
 * Список ленивый и постраничный: длинная история не тормозит экран.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HistorySection(
    history: List<HistoryEntry>,
    visible: Boolean,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onRemove: (Int, Int) -> Unit,
) {
    // Запись целиком (индекс + текст + размер): при изменении истории меню
    // не «перепрыгнет» на чужую запись.
    var menuEntry by remember { mutableStateOf<HistoryEntry?>(null) }
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
            items(items = page, key = { it.index }) { entry ->
                val value = entry.display
                val deleteLabel = stringResource(R.string.delete)
                val copyLabel = stringResource(R.string.copy)
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
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
                                onLongClick = { menuEntry = entry },
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
                        expanded = menuEntry == entry,
                        onDismissRequest = { menuEntry = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text(copyLabel) },
                            onClick = {
                                onCopy(value)
                                menuEntry = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(deleteLabel) },
                            onClick = {
                                onRemove(entry.index, entry.count)
                                menuEntry = null
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
        text = {
            Box {
                Text(stringResource(R.string.exhausted_message))
                // Конфетти сверху: весь диапазон выбран — повод для праздника.
                ConfettiOverlay(modifier = Modifier.matchParentSize())
            }
        },
        confirmButton = {
            TextButton(onClick = onNewSession) { Text(stringResource(R.string.new_session)) }
        },
        dismissButton = {
            TextButton(onClick = onResetContinue) { Text(stringResource(R.string.continue_reset)) }
        },
    )
}

/** Диалог редактирования сессии (3.2). */
@Composable
private fun EditSessionDialog(
    state: SessionUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onAllowRepeatsChange: (Boolean) -> Unit,
    onItemsChange: (String) -> Unit,
    onDiceCountChange: (String) -> Unit,
    onDiceSidesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val session = state.session ?: return
    var titleText by remember { mutableStateOf(state.editTitleText) }
    var descriptionText by remember { mutableStateOf(state.editDescriptionText) }
    var minText by remember { mutableStateOf(state.editMinText) }
    var maxText by remember { mutableStateOf(state.editMaxText) }
    var allowRepeats by remember { mutableStateOf(state.editAllowRepeats) }
    var itemsText by remember { mutableStateOf(state.editItemsText) }
    var diceCountText by remember { mutableStateOf(state.editDiceCountText) }
    var diceSidesText by remember { mutableStateOf(state.editDiceSidesText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_session)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Название
                OutlinedTextField(
                    value = titleText,
                    onValueChange = {
                        titleText = it
                        onTitleChange(it)
                    },
                    label = { Text(stringResource(R.string.edit_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Заметка
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = {
                        descriptionText = it
                        onDescriptionChange(it)
                    },
                    label = { Text(stringResource(R.string.session_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                )

                // Режим чисел: диапазон
                if (session.isNumbersMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = {
                                minText = it
                                onMinChange(it)
                            },
                            label = { Text(stringResource(R.string.edit_range_from)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = maxText,
                            onValueChange = {
                                maxText = it
                                onMaxChange(it)
                            },
                            label = { Text(stringResource(R.string.edit_range_to)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_allow_repeats),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = allowRepeats,
                            onCheckedChange = { enabled ->
                                allowRepeats = enabled
                                onAllowRepeatsChange(enabled)
                            },
                        )
                    }
                }

                // Режим списка/перемешивания: элементы
                if (session.isItemsMode || session.isShuffleMode) {
                    OutlinedTextField(
                        value = itemsText,
                        onValueChange = {
                            itemsText = it
                            onItemsChange(it)
                        },
                        label = { Text(stringResource(R.string.edit_items_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }

                // Режим кубиков
                if (session.isDiceMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = diceCountText,
                            onValueChange = {
                                diceCountText = it
                                onDiceCountChange(it)
                            },
                            label = { Text(stringResource(R.string.edit_dice_count)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = diceSidesText,
                            onValueChange = {
                                diceSidesText = it
                                onDiceSidesChange(it)
                            },
                            label = { Text(stringResource(R.string.edit_dice_sides)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
            onRemoveRoll = { _, _ -> },
            onAddManual = {},
            onCopy = {},
            onShare = {},
            onShareImage = {},
            onToggleStats = {},
            onSetSeed = {},
            onDescriptionChange = {},
            onSaveHistory = {},
            onShareHistory = {},
        )
    }
}
