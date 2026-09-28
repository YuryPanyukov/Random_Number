package CodeSyS.Random_Number.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import CodeSyS.Random_Number.App
import CodeSyS.Random_Number.ui.home.HomeViewModel
import CodeSyS.Random_Number.ui.newsession.NewSessionViewModel
import CodeSyS.Random_Number.ui.session.SessionViewModel

/** DI-контейнер приложения из [android.app.Application]. */
private fun CreationExtras.appContainer(): App =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App

/** Фабрика [HomeViewModel] с доступом к контейнеру приложения. */
val HomeViewModelFactory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T = HomeViewModel(extras.appContainer().container.sessionRepository) as T
}

/** Фабрика [NewSessionViewModel] с доступом к контейнеру приложения. */
val NewSessionViewModelFactory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T = NewSessionViewModel(extras.appContainer().container.sessionRepository) as T
}

/** Фабрика [SessionViewModel] с доступом к контейнеру приложения. */
val SessionViewModelFactory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        val container = extras.appContainer().container
        return SessionViewModel(
            repository = container.sessionRepository,
            generator = container.numberGenerator,
        ) as T
    }
}
