package CodeSyS.Random_Number

import CodeSyS.Random_Number.data.DataStoreSessionRepository
import CodeSyS.Random_Number.data.DataStoreSettingsRepository
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.data.SettingsRepository
import CodeSyS.Random_Number.domain.NumberGenerator
import CodeSyS.Random_Number.platform.FeedbackProvider
import CodeSyS.Random_Number.platform.SystemFeedbackProvider
import android.app.Application
import android.content.Context

/**
 * Простой DI-контейнер (без фреймворка): единственные зависимости приложения.
 */
class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    val sessionRepository: SessionRepository = DataStoreSessionRepository(context)
    val numberGenerator: NumberGenerator = NumberGenerator()
    val feedbackProvider: FeedbackProvider = SystemFeedbackProvider(
        context = context.applicationContext,
        settingsRepository = settingsRepository,
    )

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
