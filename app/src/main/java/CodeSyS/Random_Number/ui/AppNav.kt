package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.App
import CodeSyS.Random_Number.ui.newsession.NewSessionMode
import CodeSyS.Random_Number.ui.newsession.NewSessionViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val HOME = "home"
    const val MODE_PICKER = "mode_picker"
    const val NEW_SESSION = "new_session/{mode}"
    const val SETTINGS = "settings"
    const val SESSION = "session/{sessionId}"
    fun newSession(mode: NewSessionMode) = "new_session/${mode.name}"
    fun session(id: String) = "session/$id"
}

/** Разбирает аргумент маршрута `new_session/{mode}` (неизвестное → числа). */
private fun String?.toNewSessionMode(): NewSessionMode =
    NewSessionMode.entries.firstOrNull { it.name == this } ?: NewSessionMode.NUMBERS

/** Intent action ярлыка «Новая сессия» (см. `res/xml/shortcuts.xml`). */
const val ACTION_NEW_SESSION = "CodeSyS.Random_Number.action.NEW_SESSION"

/** Intent action ярлыка «Последняя сессия». */
const val ACTION_LAST_SESSION = "CodeSyS.Random_Number.action.LAST_SESSION"

/** Что открыть при запуске приложения (в том числе по ярлыку рабочего стола). */
enum class AppStartAction {
    /** Обычный запуск: остаёмся в главном меню. */
    NONE,

    /** Открыть экран выбора режима новой сессии. */
    NEW_SESSION,

    /** Открыть последнюю использованную сессию. */
    LAST_SESSION,
}

/**
 * Отображает action ярлыка в [AppStartAction]: чистая функция, покрыта тестом.
 */
fun startActionFor(action: String?): AppStartAction = when (action) {
    ACTION_NEW_SESSION -> AppStartAction.NEW_SESSION
    ACTION_LAST_SESSION -> AppStartAction.LAST_SESSION
    else -> AppStartAction.NONE
}

/**
 * Корневая навигация: главное меню → параметры новой сессии → генерация.
 *
 * @param startAction действие ярлыка, с которым открылось приложение
 * ([AppStartAction.NONE] для обычного запуска).
 * @param onStartActionHandled вызывается после отработки [startAction],
 * чтобы вызывающий сбросил его и переход не повторялся.
 */
@Composable
fun AppNav(
    startAction: AppStartAction = AppStartAction.NONE,
    onStartActionHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Переход по ярлыку: выбор режима либо последняя сессия.
    LaunchedEffect(startAction) {
        when (startAction) {
            AppStartAction.NONE -> return@LaunchedEffect

            AppStartAction.NEW_SESSION -> navController.navigate(Routes.MODE_PICKER) {
                launchSingleTop = true
            }

            AppStartAction.LAST_SESSION -> {
                val app = context.applicationContext as? App
                val id = app?.container?.sessionRepository
                    ?.observeSessionsOnce()
                    ?.firstOrNull()
                    ?.id
                if (id != null) {
                    navController.navigate(Routes.session(id)) { launchSingleTop = true }
                }
            }
        }
        onStartActionHandled()
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewSession = { navController.navigate(Routes.MODE_PICKER) },
                onOpenSession = { id -> navController.navigate(Routes.session(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        // Первый шаг создания сессии: выбор режима генерации (2.6).
        composable(Routes.MODE_PICKER) {
            ModePickerScreen(
                onBack = { navController.popBackStack() },
                onModeSelected = { mode -> navController.navigate(Routes.newSession(mode)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.NEW_SESSION,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode").toNewSessionMode()
            val viewModel: NewSessionViewModel =
                viewModel(factory = newSessionViewModelFactory(mode))

            // Сессия создана — открываем её экран и убираем форму и выбор
            // режима из стека, чтобы «назад» вёл сразу в главное меню.
            LaunchedEffect(Unit) {
                viewModel.createdSessionId.collect { id ->
                    navController.navigate(Routes.session(id)) {
                        popUpTo(Routes.MODE_PICKER) { inclusive = true }
                    }
                }
            }

            NewSessionScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    // Переход выполняется по событию createdSessionId.
                },
                initialMode = mode,
            )
        }

        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            SessionScreen(
                sessionId = backStackEntry.arguments?.getString("sessionId"),
                // Выход из сессии всегда возвращает в главное окно,
                // минуя промежуточные записи стека (форма, старая сессия).
                onBack = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNewSession = { navController.navigate(Routes.MODE_PICKER) },
            )
        }
    }
}
