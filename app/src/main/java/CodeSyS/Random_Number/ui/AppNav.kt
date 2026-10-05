package CodeSyS.Random_Number.ui

import CodeSyS.Random_Number.ui.newsession.NewSessionMode
import CodeSyS.Random_Number.ui.newsession.NewSessionViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Корневая навигация: главное меню → параметры новой сессии → генерация. */
@Composable
fun AppNav() {
    val navController = rememberNavController()

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
