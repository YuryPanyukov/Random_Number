package CodeSyS.Random_Number

import CodeSyS.Random_Number.data.AppSettings
import CodeSyS.Random_Number.ui.AppNav
import CodeSyS.Random_Number.ui.AppStartAction
import CodeSyS.Random_Number.ui.LocalSessionTexts
import CodeSyS.Random_Number.ui.rememberSessionTexts
import CodeSyS.Random_Number.ui.startActionFor
import CodeSyS.Random_Number.ui.theme.RandomNumbersTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /** Действие ярлыка, с которым открылось приложение (см. `shortcuts.xml`). */
    private val startAction = mutableStateOf(AppStartAction.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startAction.value = startActionFor(intent.action)

        val settingsRepository = (application as App).container.settingsRepository

        setContent {
            // Настройки темы/акцента из DataStore применяются на лету.
            val settings by settingsRepository.settings
                .collectAsState(initial = AppSettings.DEFAULT)

            // Строки локали доступны всем экранам (заголовки сессий,
            // шаринг, экспорт) без протягивания ресурсов через VM.
            CompositionLocalProvider(LocalSessionTexts provides rememberSessionTexts()) {
                RandomNumbersTheme(
                    themeMode = settings.themeMode,
                    accent = settings.accent,
                ) {
                    AppNav(
                        startAction = startAction.value,
                        onStartActionHandled = { startAction.value = AppStartAction.NONE },
                    )
                }
            }
        }
    }

    /**
     * Приложение открыто заново по ярлыку (активность уже в стеке):
     * обрабатываем новый intent и повторяем стартовый переход.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startAction.value = startActionFor(intent.action)
    }
}
