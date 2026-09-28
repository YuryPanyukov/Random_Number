package CodeSyS.Random_Number

import android.app.Application
import android.content.Context
import CodeSyS.Random_Number.data.DataStoreSessionRepository
import CodeSyS.Random_Number.data.SessionRepository
import CodeSyS.Random_Number.domain.NumberGenerator

/**
 * Простой DI-контейнер (без фреймворка): единственные зависимости приложения.
 */
class AppContainer(context: Context) {
    val sessionRepository: SessionRepository = DataStoreSessionRepository(context)
    val numberGenerator: NumberGenerator = NumberGenerator()
}

/** Точка входа: создаёт [container] и делится им со всем приложением. */
class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
