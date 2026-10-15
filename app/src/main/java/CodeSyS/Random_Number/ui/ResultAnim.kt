package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.domain.CoinSide
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Анимированные карточки результата для режимов монеты и кубиков.
 *
 * При каждом новом броске токены (монеты или кубики) коротко
 * «прокручивают» случайные грани, после чего устанавливаются на итоговое
 * значение с упругим скачком масштаба. В наборе токены стартуют со сдвигом
 * (stagger), чтобы несколько монет/кубиков бросались с переливом.
 *
 * Триггер анимации — внешний «тик поколения» [nonce]: экран передаёт
 * [CodeSyS.Random_Number.data.Session.generatedCount], поэтому анимация
 * запускается на каждом новом броске, отмене («отменить последнее»)
 * и сбросе истории.
 */

/** Как часто токен показывает новую грань во время прокрутки. */
private const val CYCLE_MILLIS = 70

/** Сколько случайных граней показывается до установки итога. */
private const val CYCLES = 7

/** Сдвиг старта для токена с номером [index] в наборе (не бесконечный). */
private fun staggerMillis(index: Int): Int = (index * 60).coerceAtMost(420)

/** Максимальный угол наклона токена во время прокрутки. */
private const val ROLL_TILT_DEGREES = 14f

/**
 * Запускает «прокрутку» токена: [CYCLES] случайных смен граней с наклоном,
 * затем возврат наклона в ноль перед установкой итога.
 */
private suspend fun runRoll(
    rotation: Animatable<Float, *>,
    initialOffsetMillis: Long,
    onCycle: (Int) -> Unit,
) {
    delay(initialOffsetMillis)
    repeat(CYCLES) { cycle ->
        onCycle(cycle)
        rotation.animateTo(
            if (cycle % 2 == 0) -ROLL_TILT_DEGREES else ROLL_TILT_DEGREES,
            tween(CYCLE_MILLIS),
        )
    }
    rotation.animateTo(0f, tween(CYCLE_MILLIS / 2))
}

/** Упругая установка итога: сначала ужимаем токен, затем пружиной к 1. */
private suspend fun Animatable<Float, *>.settle() {
    snapTo(0.72f)
    animateTo(
        1f,
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    )
}

/**
 * Карточка результата режима монеты: [sides] — последний бросок
 * (одна монета на сторону; размер ровно размеру батча). Долгое нажатие
 * копирует стороны текстом, разделённые « + », как история.
 */
@Composable
fun CoinResultCard(
    sides: List<CoinSide>,
    nonce: Int,
    sideLabel: (CoinSide) -> String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ResultCard(
        title = stringResource(R.string.last_result),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        copyValue = sides.joinToString(" + ") { sideLabel(it) },
        onCopy = onCopy,
        modifier = modifier,
    ) {
        if (sides.isEmpty()) {
            Dash(color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            val single = sides.size == 1
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                sides.forEachIndexed { index, side ->
                    CoinToken(
                        target = side,
                        nonce = nonce,
                        index = index,
                        fullLabel = sideLabel(side),
                        size = if (single) 88.dp else 64.dp,
                    )
                }
            }
        }
    }
}

/**
 * Карточка результата режима кубиков: значения [values] последнего броска
 * с [diceFaces] гранями и сумма броска крупным шрифтом. Ряд кубиков
 * центрируется; для больших наборов размер уменьшается.
 */
@Composable
fun DiceResultCard(
    values: List<Int>,
    diceFaces: Int,
    nonce: Int,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ResultCard(
        title = stringResource(R.string.last_result),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        copyValue = values.joinToString(" + "),
        onCopy = onCopy,
        modifier = modifier,
    ) {
        if (values.isEmpty()) {
            Dash(color = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            val dieSize = if (values.size <= 3) 64.dp else 52.dp
            val dieDesc = stringResource(R.string.dice_token_desc)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                values.forEachIndexed { index, value ->
                    DiceToken(
                        target = value,
                        faces = diceFaces,
                        nonce = nonce,
                        index = index,
                        size = dieSize,
                        desc = dieDesc,
                    )
                }
            }
            if (values.size > 1) {
                Spacer(Modifier.height(8.dp))
                AnimatedResultText(
                    text = values.sum().toString(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** Заглушка пустого результата: «—». */
@Composable
private fun Dash(color: Color) {
    Text(
        text = "—",
        style = MaterialTheme.typography.displayMedium,
        color = color,
    )
}

/**
 * Оболочка карточки результата: как [BigNumberCard] (заголовок «содержимое»,
 * долгое нажатие — меню «Копировать»), но с произвольным содержимым.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultCard(
    title: String,
    containerColor: Color,
    contentColor: Color,
    copyValue: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.copy)

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor)) {
        // Долгое нажатие в любом месте карточки — меню копирования.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = copyLabel,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Spacer(Modifier.height(12.dp))
                content()
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(copyLabel) },
                    onClick = {
                        onCopy(copyValue)
                        menuOpen = false
                    },
                )
            }
        }
    }
}

/** Крупное итоговое значение с «поп»-появлением при смене. */
@Composable
private fun AnimatedResultText(
    text: String,
    color: Color,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (
                fadeIn(tween(60)) +
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                ).togetherWith(fadeOut(tween(40)))
        },
    ) { label ->
        // TalkBack озвучивает итог сразу после генерации.
        Text(
            text = label,
            style = MaterialTheme.typography.displayMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Монета: круг с первой буквой стороны (О/Р; в английской локали H/T). */
@Composable
private fun CoinToken(
    target: CoinSide,
    nonce: Int,
    index: Int,
    fullLabel: String,
    size: Dp,
) {
    var face by remember { mutableIntStateOf(target.ordinal) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val settleDelay = staggerMillis(index) + (CYCLES + 1) * CYCLE_MILLIS / 2

    LaunchedEffect(nonce) {
        runRoll(rotation, staggerMillis(index).toLong()) {
            face = Random.nextInt(CoinSide.entries.size)
        }
        face = target.ordinal
    }
    LaunchedEffect(nonce) {
        delay(settleDelay.toLong())
        scale.settle()
    }

    val faceText = CoinSide.of(face).label.take(1)
    val shape = CircleShape

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = fullLabel }
            .clip(shape)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = faceText,
            style = if (size >= 80.dp) {
                MaterialTheme.typography.displayMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Кубик: скруглённый квадрат с числом граней 1..[faces]. */
@Composable
private fun DiceToken(
    target: Int,
    faces: Int,
    nonce: Int,
    index: Int,
    size: Dp,
    desc: String,
) {
    var faceValue by remember { mutableIntStateOf(target) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val settleDelay = staggerMillis(index) + (CYCLES + 1) * CYCLE_MILLIS / 2

    LaunchedEffect(nonce) {
        runRoll(rotation, staggerMillis(index).toLong()) {
            faceValue = Random.nextInt(1, faces + 1)
        }
        faceValue = target
    }
    LaunchedEffect(nonce) {
        delay(settleDelay.toLong())
        scale.settle()
    }

    val shape = RoundedCornerShape((size / 5).coerceAtLeast(10.dp))
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = "$desc: $target" }
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = faceValue.toString(),
            style = if (size >= 60.dp) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineSmall
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Предпросмотр: несколько монет. */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PreviewCoinCard() {
    CodeSyS.Random_Number.ui.theme.RandomNumbersTheme {
        CoinResultCard(
            sides = listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.HEADS),
            nonce = 1,
            sideLabel = { it.label },
            onCopy = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Предпросмотр: бросок 2d6 + много монет. */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PreviewDiceCard() {
    CodeSyS.Random_Number.ui.theme.RandomNumbersTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            DiceResultCard(values = listOf(4, 2, 6), diceFaces = 6, nonce = 1, onCopy = {})
            CoinResultCard(
                sides = listOf(CoinSide.HEADS),
                nonce = 1,
                sideLabel = { it.label },
                onCopy = {},
            )
        }
    }
}
