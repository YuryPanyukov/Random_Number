package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.domain.SessionStats
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Карточка со статистикой по истории сессии.
 *
 * Данные приходят из [SessionStats] — чистой функции, поэтому
 * само отображение тривиально и не требует состояния.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionStatsCard(
    stats: SessionStats,
    modifier: Modifier = Modifier,
) {
    if (stats.count == 0) {
        Text(
            text = stringResource(R.string.stats_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium,
            )

            StatsRow(
                label = stringResource(R.string.stats_generated),
                value = pluralStringResource(R.plurals.history_count, stats.count, stats.count),
            )
            StatsRow(
                label = stringResource(R.string.stats_unique),
                value = stats.unique.toString(),
            )
            if (stats.duplicates > 0) {
                StatsRow(
                    label = stringResource(R.string.stats_duplicates),
                    value = stats.duplicates.toString(),
                )
            }
            StatsRow(
                label = stringResource(R.string.stats_range),
                value = stringResource(R.string.stats_range_value, stats.min ?: 0, stats.max ?: 0),
            )
            stats.average?.let {
                StatsRow(label = stringResource(R.string.stats_average), value = formatStat(it))
            }
            stats.median?.let {
                StatsRow(label = stringResource(R.string.stats_median), value = formatStat(it))
            }
            stats.mostFrequent?.let {
                StatsRow(
                    label = stringResource(R.string.stats_most_frequent),
                    value = "${it.value} (${it.count})",
                )
            }
            stats.leastFrequent?.let {
                StatsRow(
                    label = stringResource(R.string.stats_least_frequent),
                    value = "${it.value} (${it.count})",
                )
            }
        }
    }
}

/** Одна строка «подпись — значение». */
@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Форматирует дробное значение: без «.0» для целых. */
private fun formatStat(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Preview(showBackground = true)
@Composable
private fun SessionStatsCardPreview() {
    RandomNumbersTheme {
        SessionStatsCard(
            stats = SessionStats(
                count = 10,
                unique = 8,
                min = 1,
                max = 42,
                average = 21.5,
                median = 20.0,
                mostFrequent = SessionStats.Frequent(7, 3),
                leastFrequent = SessionStats.Frequent(13, 1),
                duplicates = 2,
            ),
        )
    }
}
