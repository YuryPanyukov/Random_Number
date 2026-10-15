package CodeSyS.Random_Number.widget

import CodeSyS.Random_Number.App
import CodeSyS.Random_Number.MainActivity
import CodeSyS.Random_Number.R
import CodeSyS.Random_Number.ui.ResourceSessionTexts
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

/**
 * Виджет «последняя сессия» (задача 5.4): показывает последнее значение
 * самой недавней сессии и позволяет сгенерировать следующее прямо с
 * домашнего экрана, не открывая приложение.
 *
 * Кнопка запускает [GenerateAction] — один шаг в последней сессии и
 * перерисовка виджета. Тап по значению открывает приложение.
 */
class LatestSessionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Сессия читается до композиции: внутри provideContent suspend-вызовы
        // недоступны. Данные — снимок на момент обновления виджета.
        val session = appContainer(context).container.sessionRepository
            .observeSessions()
            .first()
            .firstOrNull()

        // Строки читаются до композиции: в Glance нет stringResource,
        // а локализация всё равно нужна.
        val texts = ResourceSessionTexts(context.resources)
        val generateLabel = context.getString(R.string.widget_generate)
        val emptyLabel = context.getString(R.string.widget_no_sessions)
        val sessionTitle = session?.let { texts.title(it) }
        val lastValue = session?.lastDisplay(texts) ?: "—"

        provideContent {
            GlanceTheme {
                WidgetContent(
                    sessionTitle = sessionTitle,
                    lastValue = lastValue,
                    generateLabel = generateLabel,
                    emptyLabel = emptyLabel,
                )
            }
        }
    }

    /** Просит систему перерисовать все экземпляры виджета. */
    suspend fun refresh(context: Context) = updateAll(context)
}

/** DI-контейнер приложения из [Context]. */
private fun appContainer(context: Context): App =
    context.applicationContext as App

/** Содержимое виджета: заголовок сессии, значение и кнопка генерации. */
@Composable
private fun WidgetContent(
    sessionTitle: String?,
    lastValue: String,
    generateLabel: String,
    emptyLabel: String,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (sessionTitle == null) {
            Text(
                text = emptyLabel,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            return@Column
        }

        Text(
            text = sessionTitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        // Значение — тап открывает приложение (крупный результат).
        Text(
            text = lastValue,
            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = generateLabel,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.primary)
                    .clickable(actionRunCallback<GenerateAction>())
                    .padding(8.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * Callback кнопки виджета: один шаг в последней сессии и перерисовка.
 *
 * Запускается вне композиции, поэтому работает только с `Context`.
 */
class GenerateAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = (context.applicationContext as App).container
        container.quickGenerate.generateLatest()
        LatestSessionWidget().refresh(context)
    }
}

/** Receiver виджета — точка входа для системы. */
class LatestSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LatestSessionWidget()
}
