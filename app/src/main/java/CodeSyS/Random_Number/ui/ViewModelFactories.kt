package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.App
import CodeSyS.Random_Number.ui.home.HomeViewModel
import CodeSyS.Random_Number.ui.newsession.NewSessionMode
import CodeSyS.Random_Number.ui.newsession.NewSessionViewModel
import CodeSyS.Random_Number.ui.session.SessionViewModel
import CodeSyS.Random_Number.ui.settings.SettingsViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** DI-контейнер приложения из [android.app.Application]. */
private fun CreationExtras.appContainer(): App =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App

/**
 * Фабрики ViewModel, собранные декларативно (`viewModelFactory`):
 * каждая реализация — один `initializer` вместо объекта-реализации
 * [ViewModelProvider.Factory] с одинаковым кодом.
 */
val HomeViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(appContainer().container.sessionRepository) }
}

/**
 * Фабрика формы новой сессии с предвыбранным режимом (экран выбора, 2.6).
 *
 * Маршрут `new_session/{mode}` создаёт VM с этим режимом, поэтому форма
 * открывается сразу с нужными полями.
 */
fun newSessionViewModelFactory(mode: NewSessionMode) = viewModelFactory {
    initializer {
        NewSessionViewModel(
            repository = appContainer().container.sessionRepository,
            initialMode = mode,
        )
    }
}

val SessionViewModelFactory = viewModelFactory {
    initializer {
        val container = appContainer().container
        SessionViewModel(
            repository = container.sessionRepository,
            generator = container.numberGenerator,
            feedback = container.feedbackProvider,
            onHistoryChanged = container::refreshLatestSessionWidget,
        )
    }
}

val SettingsViewModelFactory = viewModelFactory {
    initializer { SettingsViewModel(appContainer().container.settingsRepository) }
}
