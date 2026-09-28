package CodeSyS.Random_Number.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import CodeSyS.Random_Number.ui.newsession.NewSessionViewModel

private object Routes {
    const val HOME = "home"
    const val NEW_SESSION = "new_session"
    const val SESSION = "session/{sessionId}"
    fun session(id: String) = "session/$id"
}

/** Корневая навигация: главное меню → параметры новой сессии → генерация. */
@Composable
fun AppNav() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewSession = { navController.navigate(Routes.NEW_SESSION) },
                onOpenSession = { id -> navController.navigate(Routes.session(id)) },
            )
        }

        composable(Routes.NEW_SESSION) {
            val viewModel: NewSessionViewModel = viewModel(factory = NewSessionViewModelFactory)

            // Сессия создана — открываем её экран и убираем форму из стека.
            LaunchedEffect(Unit) {
                viewModel.createdSessionId.collect { id ->
                    navController.navigate(Routes.session(id)) {
                        popUpTo(Routes.NEW_SESSION) { inclusive = true }
                    }
                }
            }

            NewSessionScreen(
                onBack = { navController.popBackStack() },
                onCreated = viewModel::createSession,
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
                onNewSession = { navController.navigate(Routes.NEW_SESSION) },
            )
        }
    }
}
