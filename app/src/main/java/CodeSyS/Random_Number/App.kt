package CodeSyS.Random_Number

import CodeSyS.Random_Number.data.DataStoreSessionRepository
import CodeSyS.Random_Number.data.DataStoreSettingsRepository
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.data.SettingsRepository
import CodeSyS.Random_Number.domain.NumberGenerator
import CodeSyS.Random_Number.domain.SessionGenerator
import CodeSyS.Random_Number.platform.FeedbackProvider
import CodeSyS.Random_Number.platform.QuickGenerate
import CodeSyS.Random_Number.platform.SystemFeedbackProvider
import CodeSyS.Random_Number.widget.LatestSessionWidget
import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Простой DI-контейнер (без фреймворка): единственные зависимости приложения.
 */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    /** Область для фоновых задач уровня приложения (обновление виджета). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    val sessionRepository: SessionRepository = DataStoreSessionRepository(context)
    val numberGenerator: NumberGenerator = NumberGenerator()
    val feedbackProvider: FeedbackProvider = SystemFeedbackProvider(
        context = context.applicationContext,
        settingsRepository = settingsRepository,
    )

    /** Генерация из виджета/плитки без открытия приложения (задача 5.4). */
    val quickGenerate: QuickGenerate = QuickGenerate(
        repository = sessionRepository,
        generator = SessionGenerator(numberGenerator = numberGenerator),
    )

    /**
     * Перерисовывает виджет «последняя сессия» после изменения данных
     * из приложения (виджет показывает снимок, а не live-данные).
     */
    fun refreshLatestSessionWidget() {
        appScope.launch { LatestSessionWidget().refresh(appContext) }
    }

    /** Освобождает ресурсы, удерживаемые контейнером (системные сервисы). */
    fun release() {
        feedbackProvider.release()
    }
}

/** Точка входа: создаёт [container] и делится им со всем приложением. */
class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.release()
        super.onTerminate()
    }
}
