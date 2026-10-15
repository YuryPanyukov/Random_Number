package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Гистограмма распределения значений сессии (3.10).
 *
 * Значения группируются в корзины: не больше [MAX_BUCKETS]; если
 * значений в диапазоне меньше — по столбику на значение. Максимальный
 * столбец подсвечивается цветом акцента. При новых значениях высоты
 * растут короткой анимацией.
 *
 * @param values журнал сессии (числа или значения кубиков).
 * @param min нижняя граница значений (у кубиков — 1).
 * @param max верхняя граница значений (у кубиков — число граней).
 */
@Composable
fun SessionHistogram(
    values: List<Int>,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty() || min > max) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.histogram_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))

        val buckets = remember(values, min, max) { bucketCounts(values, min, max) }
        val maxCount = remember(buckets) { buckets.max() }
        val progress = remember(buckets) { Animatable(0f) }

        LaunchedEffect(buckets) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(HISTOGRAM_MILLIS))
        }

        // Цвета действительны только в composable-контексте — берём заранее.
        val barColor = MaterialTheme.colorScheme.primary
        val highlightColor = MaterialTheme.colorScheme.tertiary

        Row(verticalAlignment = Alignment.Bottom) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(HISTOGRAM_HEIGHT_DP.dp),
            ) {
                val count = buckets.size
                if (count == 0) return@Canvas
                val gap = size.width * 0.02f
                val barWidth = (size.width - gap * (count - 1)) / count
                buckets.forEachIndexed { index, countInBucket ->
                    val barHeight = size.height * countInBucket / maxCount * progress.value
                    if (barHeight <= 0f) return@forEachIndexed
                    drawRoundRect(
                        color = if (countInBucket == maxCount) highlightColor else barColor,
                        topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        // Слева-направо скругление выглядит как «столбик».
                        cornerRadius = CornerRadius(barWidth * 0.12f),
                    )
                }
            }
        }

        Row {
            Text(
                text = min.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = max.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Не больше такого числа корзин. */
private const val MAX_BUCKETS = 20

/** Высота области столбцов (dp). */
private const val HISTOGRAM_HEIGHT_DP = 96

/** Длительность анимации роста столбцов. */
private const val HISTOGRAM_MILLIS = 400

/**
 * Считает столбцы: по значению, если значений в диапазоне немного;
 * иначе — равные корзины, чтобы дыры широких диапазонов не съедали картинку.
 */
private fun bucketCounts(values: List<Int>, min: Int, max: Int): List<Int> {
    val range = max - min + 1
    val buckets = MutableList(MAX_BUCKETS.coerceAtMost(range)) { 0 }
    val bucketSize = ceil(range.toDouble() / buckets.size).toInt()
    values.forEach { value ->
        val index = ((value - min) / bucketSize).coerceIn(0, buckets.lastIndex)
        buckets[index] += 1
    }
    return buckets
}
